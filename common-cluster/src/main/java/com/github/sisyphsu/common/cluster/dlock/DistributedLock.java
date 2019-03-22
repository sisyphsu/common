package com.github.sisyphsu.common.cluster.dlock;

import com.github.sisyphsu.common.cluster.cid.ClusterID;
import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Redis操作封装, 它需要使用Jedis直接连接Redis, 再Redis宕机时需要能够感知到。
 * <p>
 * created by sulin at 2018-11-05 12:08:10
 */
@Slf4j
public class DistributedLock {

    private static final String ACQUIRE_FILE = "lua/dlock-acquire.lua";

    private ClusterID clusterID;
    private StringRedisTemplate template;
    private DistributedLockProperties props;

    /**
     * 分布式锁监听器
     */
    private DistributedLockMonitor monitor;
    /**
     * 当前已锁定的KEY
     */
    private Set<String> lockedKeys = new ConcurrentSkipListSet<>();
    /**
     * 占用锁的lua脚本
     */
    private RedisScript<String> acquireScript;

    private Future future;

    public DistributedLock(ClusterID clusterID, StringRedisTemplate template, DistributedLockProperties props) {
        this.clusterID = clusterID;
        this.template = template;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        this.acquireScript = new DefaultRedisScript<>(loadResource(ACQUIRE_FILE), String.class);
        this.monitor = new DistributedLockMonitor(props.getChannel(), () -> {
            if (this.template != null && this.template.getConnectionFactory() != null) {
                return this.template.getConnectionFactory().getConnection();
            }
            return null;
        });
        this.future = ScheduleUtils.runEvery(props.getFlushIntervalSecond() * 1000, this::flushAllLock);
    }

    @PreDestroy
    public void destory() {
        this.future.cancel(true);
    }

    /**
     * 在并发锁内部执行业务逻辑
     *
     * @param keys 需锁定的KEY
     * @param run  业务执行逻辑
     * @throws Exception 执行失败
     */
    public void runInLock(List<String> keys, Runnable run) throws Exception {
        this.runInLock(keys, () -> {
            run.run();
            return null;
        });
    }

    /**
     * 在并发锁内部执行业务逻辑
     *
     * @param keys 需锁定的KEY
     * @param run  业务单元
     * @param <T>  返回类型
     * @return 返回数据
     * @throws Exception 并发锁异常or业务异常
     */
    public <T> T runInLock(List<String> keys, Supplier<T> run) throws Exception {
        if (!this.lock(keys, 3000)) {
            throw new InterruptedException("竞争并发锁失败");
        }
        try {
            return run.get();
        } finally {
            this.unlock(keys);
        }
    }

    /**
     * 锁定指定KEY, 如果不成功则阻塞等待一段时间
     *
     * @param keys      需锁定的KEY
     * @param timeoutMS 超时时间
     * @return 释放成功
     */
    public boolean lock(List<String> keys, int timeoutMS) {
        long futureTime = System.currentTimeMillis() + timeoutMS;
        boolean hasLock;
        while (true) {
            hasLock = this.tryLock(keys);
            if (hasLock || System.currentTimeMillis() >= futureTime) {
                break;
            }
            log.trace("分布式锁竞争失败, 进入等待: {}", keys);
            Semaphore sema = new Semaphore(0);
            monitor.addListener(keys, sema);
            ScheduleUtils.runAfter(timeoutMS, sema::release);
            ScheduleUtils.runSeliently(sema::acquire);
            monitor.delListener(keys, sema);
        }
        if (hasLock) {
            this.lockedKeys.addAll(keys); // 记录抢到的锁
        }
        return hasLock;
    }

    /**
     * 尝试锁定指定KEYS
     *
     * @param keys 待锁定的KEYS
     * @return 是否成功
     */
    public boolean tryLock(List<String> keys) {
        monitor.blockIfRisk();
        for (String key : keys) {
            if (this.lockedKeys.contains(key)) {
                return false;
            }
        }
        String token = String.valueOf(clusterID.get());
        String expire = String.valueOf(props.getExpireSecond());
        String result = template.execute(this.acquireScript, wrapKeys(keys), token, expire);
        if (StringUtils.equalsIgnoreCase(result, "ok")) {
            return true;
        }
        if (StringUtils.isNotEmpty(result)) {
            log.warn("tryLock异常: {}", result);
        }
        return false;
    }

    /**
     * 尝试释放锁, 内部直接强制删除锁
     * 不需要进行容错处理, 即便内部异常导致释放失败, 锁也会自动过期失效
     *
     * @param keys 待释放的锁
     */
    public void unlock(List<String> keys) {
        this.lockedKeys.removeAll(keys);
        // 删除锁
        template.delete(wrapKeys(keys));
        // 发送消息
        monitor.publishUnlock(keys);
    }

    // 定时刷新全部已占用的锁在Redis中的失效时间
    private void flushAllLock() {
        if (lockedKeys.isEmpty()) {
            return;
        }
        log.trace("刷新分布式锁失效时间: {}", lockedKeys);
        template.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : wrapKeys(lockedKeys)) {
                connection.expire(key.getBytes(), props.getExpireSecond());
            }
            return null;
        });
    }

    // 封装Keys，添加统一的前缀
    private List<String> wrapKeys(Collection<String> keys) {
        List<String> result = new ArrayList<>();
        keys.forEach(key -> result.add(props.getPrefix() + key));
        return result;
    }

    // 加载静态资源文件
    private String loadResource(String filename) {
        try (InputStream is = DistributedLock.class.getClassLoader().getResourceAsStream(filename)) {
            if (is == null) {
                throw new IllegalStateException("can't find file: " + filename);
            }
            return IOUtils.toString(is, Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read file error: " + filename, e);
        }
    }

}
