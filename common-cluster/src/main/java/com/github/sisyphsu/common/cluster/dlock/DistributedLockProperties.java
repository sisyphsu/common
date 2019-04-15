package com.github.sisyphsu.common.cluster.dlock;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DistributedLock's configuration
 *
 * @author sulin
 * @since 2019-03-22 10:42:57
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.dlock")
public class DistributedLockProperties {

    /**
     * The lock's expire time (second), default 5.
     */
    private int expireSecond = 5;
    /**
     * The lock's flush interval (second), default 1
     */
    private int flushIntervalSecond = 1;
    /**
     * The lock's prefix, which will be used as redis prefix
     */
    private String prefix = "dlock:";
    /**
     * The lock's synchronization channel.
     */
    private String channel = "#dlock:sync";

}
