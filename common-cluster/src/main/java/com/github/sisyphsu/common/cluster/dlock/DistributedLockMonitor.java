package com.github.sisyphsu.common.cluster.dlock;

import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Monitor the distributed lock's release.
 * Will start an redis subscriber to listen unlock notification.
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
     * start unlock notification's subscriber
     */
    private void startSubscribe() {
        RedisConnection connection = supplier.get();
        if (connection == null) {
            ScheduleUtils.runEvery(1000, this::startSubscribe);
            return;
        }
        log.info("start unlock notification's subscriber");
        connection.subscribe(this::onMessage, this.channel.getBytes());
    }

    /**
     * Send the specified key's unlock notification.
     *
     * @param keys the keys was unlocked
     */
    public void publishUnlock(Collection<String> keys) {
        this.notifyUnlock(keys); // Give the current JVM a small stove
        RedisConnection connection = supplier.get();
        if (connection == null) {
            log.warn("get redis connection failed, will not publish unlock notification.");
            return;
        }
        try {
            connection.publish(channel.getBytes(), StringUtils.join(keys, KEY_SEPARATOR).getBytes());
        } finally {
            connection.close();
        }
    }

    /**
     * Check if the redis server was failed, block until it recover.
     */
    public void blockIfRisk() {
        // unsupported
    }

    /**
     * Add specified key's unlock semaphore.
     *
     * @param keys key name
     * @param sema semaphore
     */
    public void addListener(Collection<String> keys, Semaphore sema) {
        synchronized (this.semaphoneMap) {
            for (String key : keys) {
                this.semaphoneMap.put(key, sema);
            }
        }
    }

    /**
     * Delete specified key's semaphore
     *
     * @param keys key name
     * @param sema semaphore
     */
    public void delListener(Collection<String> keys, Semaphore sema) {
        synchronized (semaphoneMap) {
            for (String key : keys) {
                if (!semaphoneMap.remove(key, sema)) {
                    log.warn("delete unlock semaphore failed: {}", key);
                }
            }
        }
    }

    // redis's sub callback
    private void onMessage(Message msg, byte[] pattern) {
        String data = new String(msg.getBody());
        log.trace("receive dlock's unlock notification: {}", data);
        List<String> keys = null;
        try {
            keys = Arrays.asList(data.split(KEY_SEPARATOR));
        } catch (Exception e) {
            log.warn("parse unlock message failed: {}", data);
        }
        this.notifyUnlock(keys);
    }

    // notify that some key was unlocked by their owners.
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
