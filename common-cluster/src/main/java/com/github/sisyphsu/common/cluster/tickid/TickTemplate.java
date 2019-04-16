package com.github.sisyphsu.common.cluster.tickid;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.retry.RetryOneTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * TickID's operation wapper, which exposed in Spring's BeanFactory.
 *
 * @author sulin
 * @since 2019-04-15 20:17:05
 */
@Slf4j
@Component
public class TickTemplate {

    @Autowired
    private TickProperties tickProperties;

    @Autowired(required = false)
    private CuratorFramework curator;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * Create new TickID instance
     *
     * @param tickName tick's name
     * @return new instance
     */
    public TickID createTickID(String tickName) {
        return this.createTickID(tickName, tickProperties.getDefaultBatchSize());
    }

    /**
     * Create new TickID instance.
     * TickID dependency on redis and zookeeper.
     *
     * @param tickName  Tick's name, like `order`, `user`...
     * @param batchSize Tick's batch size
     * @return new instance
     */
    public TickID createTickID(String tickName, int batchSize) {
        TickProvider provider;
        if (this.curator != null) {
            provider = new ZooKeeperTickProvider(tickName);
        } else if (this.redisTemplate != null) {
            provider = new RedisTickProvider(tickName);
        } else {
            throw new IllegalStateException("TickID need curator/zookeeper or redis datasource");
        }
        return new TickID(provider, batchSize);
    }

    /**
     * TickProvider based on redis
     */
    private class RedisTickProvider implements TickProvider {

        private String tickName;
        private String key;

        private RedisTickProvider(String tickName) {
            this.tickName = tickName;
            this.key = String.format("%s:%s", tickProperties.getPrefix(), tickName);
        }

        @Override
        public String name() {
            return tickName;
        }

        @Override
        public long acquireTick(int count) {
            Long result = redisTemplate.opsForValue().increment(this.key, count);
            if (result == null) {
                throw new NullPointerException("redis's tick increment return null");
            }
            return result;
        }

    }

    /**
     * TickProvider based on zookeeper
     */
    private class ZooKeeperTickProvider implements TickProvider {

        private String tickName;
        private DistributedAtomicLong counter;

        private ZooKeeperTickProvider(String tickName) {
            String path = String.format("/%s/%s", tickProperties.getPrefix(), tickName);
            this.tickName = tickName;
            this.counter = new DistributedAtomicLong(curator, path, new RetryOneTime(1));
        }

        @Override
        public String name() {
            return tickName;
        }

        @Override
        public long acquireTick(int count) throws Exception {
            AtomicValue<Long> result = this.counter.add((long) count);
            if (!result.succeeded()) {
                throw new IllegalStateException("zookeeper's tick add failed");
            }
            return result.postValue();
        }

    }

}
