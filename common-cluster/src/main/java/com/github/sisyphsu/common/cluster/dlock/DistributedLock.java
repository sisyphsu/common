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
 * The distributed lock based on redis.
 *
 * @author sulin
 * @since 2018-11-05 12:08:10
 */
@Slf4j
public class DistributedLock {

    private static final String ACQUIRE_FILE = "lua/dlock-acquire.lua";

    private ClusterID clusterID;
    private StringRedisTemplate template;
    private DistributedLockProperties props;

    /**
     * unlock monitor
     */
    private DistributedLockMonitor monitor;
    /**
     * all lock hold by this instance.
     */
    private Set<String> lockedKeys = new ConcurrentSkipListSet<>();
    /**
     * dlock's lua script
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
     * Run the specified function in distributed lock.
     *
     * @param keys the keys need to be lock.
     * @param run  execute body
     * @throws Exception dlock fail or biz error
     */
    public void runInLock(List<String> keys, Runnable run) throws Exception {
        this.runInLock(keys, () -> {
            run.run();
            return null;
        });
    }

    /**
     * Run the specified function in distributed lock.
     *
     * @param keys the keys need to be lock.
     * @param run  execute body
     * @param <T>  generic type
     * @return result
     * @throws Exception dlock fail or biz error
     */
    public <T> T runInLock(List<String> keys, Supplier<T> run) throws Exception {
        if (!this.lock(keys, 3000)) {
            throw new InterruptedException("DLock competition failed");
        }
        try {
            return run.get();
        } finally {
            this.unlock(keys);
        }
    }

    /**
     * Try lock the specified keys, block for a while if not success.
     *
     * @param keys      the keys need to be locked
     * @param timeoutMS block time
     * @return whether success or not
     */
    public boolean lock(List<String> keys, int timeoutMS) {
        long futureTime = System.currentTimeMillis() + timeoutMS;
        boolean hasLock;
        while (true) {
            hasLock = this.tryLock(keys);
            if (hasLock || System.currentTimeMillis() >= futureTime) {
                break;
            }
            log.debug("dlock competition fails, enters waiting: {}", keys);
            Semaphore sema = new Semaphore(0);
            monitor.addListener(keys, sema);
            ScheduleUtils.runAfter(timeoutMS, sema::release);
            ScheduleUtils.runSeliently(sema::acquire);
            monitor.delListener(keys, sema);
        }
        if (hasLock) {
            this.lockedKeys.addAll(keys); // record the lock
        }
        return hasLock;
    }

    /**
     * Try lock the specified keys
     *
     * @param keys the keys need to be locked
     * @return whether success or not
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
            log.warn("tryLock failed: {}", result);
        }
        return false;
    }

    /**
     * Try unlock, will delete the key directly.
     * No fault-tolerant processing is required, and even if the internal exception
     * causes the release to fail, the lock will automatically expire.
     *
     * @param keys the keys to unlock
     */
    public void unlock(List<String> keys) {
        this.lockedKeys.removeAll(keys);
        // delete the lock
        template.delete(wrapKeys(keys));
        // notify other waiter
        monitor.publishUnlock(keys);
    }

    private void flushAllLock() {
        if (lockedKeys.isEmpty()) {
            return;
        }
        log.trace("flush dlock's expired time: {}", lockedKeys);
        template.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : wrapKeys(lockedKeys)) {
                connection.expire(key.getBytes(), props.getExpireSecond());
            }
            return null;
        });
    }

    private List<String> wrapKeys(Collection<String> keys) {
        List<String> result = new ArrayList<>();
        keys.forEach(key -> result.add(props.getPrefix() + key));
        return result;
    }

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
