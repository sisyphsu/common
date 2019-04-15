package com.github.sisyphsu.common.cluster.dlock;

import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnection;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * 监视分布式锁的释放和丢失.
 * 单独启动线程监听Redis锁释放的消息, 并支持锁释放的订阅
 *
 * @author sulin
 * @since 2018-11-05 11:37:01
 */
@Slf4j
public class DistributedLockMonitor {

    private static final String KEY_SEPARATOR = ",";

    private String channel;
    private Supplier<RedisConnection> supplier;
    private final Multimap<String, Semaphore> semaphoneMap = ArrayListMultimap.create();

    public DistributedLockMonitor(String channel, Supplier<RedisConnection> supplier) {
        this.channel = channel;
        this.supplier = supplier;
        this.startSubscribe();
    }

    /**
     * 启动unlock订阅
     */
    private void startSubscribe() {
        RedisConnection connection = supplier.get();
        if (connection == null) {
            ScheduleUtils.runEvery(1000, this::startSubscribe);
            return;
        }
        log.info("启动监听器");
        connection.subscribe(this::onMessage, this.channel.getBytes());
    }

    /**
     * 发送keys并发锁释放的消息
     *
     * @param keys 释放锁的KEYS
     */
    public void publishUnlock(Collection<String> keys) {
        this.notifyUnlock(keys); // 给当前节点开私灶
        RedisConnection connection = supplier.get();
        if (connection == null) {
            log.warn("获取redis连接失败, 放弃广播");
            return;
        }
        try {
            connection.publish(channel.getBytes(), StringUtils.join(keys, KEY_SEPARATOR).getBytes());
        } finally {
            connection.close();
        }
    }

    /**
     * 如果当前处于风险状态则阻塞直至风险恢复
     */
    public void blockIfRisk() {
        // 暂不支持此功能
    }

    /**
     * 添加指定KEY的锁释放的回调函数
     *
     * @param keys KEY名称
     * @param sema 回调函数
     */
    public void addListener(Collection<String> keys, Semaphore sema) {
        synchronized (this.semaphoneMap) {
            for (String key : keys) {
                this.semaphoneMap.put(key, sema);
            }
        }
    }

    /**
     * 删除指定KEY的锁释放回调函数
     *
     * @param keys KEY名称
     * @param sema 回调函数
     */
    public void delListener(Collection<String> keys, Semaphore sema) {
        synchronized (semaphoneMap) {
            for (String key : keys) {
                if (!semaphoneMap.remove(key, sema)) {
                    log.warn("删除unlock监听器失败: {}", key);
                }
            }
        }
    }

    // SUB消息回调函数
    private void onMessage(Message msg, byte[] pattern) {
        String data = new String(msg.getBody());
        log.trace("监听到unlock消息: {}", data);
        List<String> keys = null;
        try {
            keys = Arrays.asList(data.split(KEY_SEPARATOR));
        } catch (Exception e) {
            log.warn("解析unlock消息失败: {}", data);
        }
        this.notifyUnlock(keys);
    }

    // 通知有一批KEY被解锁了
    private void notifyUnlock(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        synchronized (semaphoneMap) {
            for (String key : keys) {
                for (Semaphore semaphore : semaphoneMap.get(key)) {
                    semaphore.release();
                }
            }
        }
    }

}
