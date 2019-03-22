package com.github.sisyphsu.common.cluster.dlock;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分布式锁配置
 *
 * @author sulin
 * @since 2019-03-22 10:42:57
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.dlock")
public class DistributedLockProperties {

    /**
     * 并发锁有效期, 默认5秒
     */
    private int expireSecond = 5;
    /**
     * 并发锁刷新间隔, 默认1秒
     */
    private int flushIntervalSecond = 1;
    /**
     * 并发锁统一前缀
     */
    private String prefix = "dlock_";
    /**
     * 并发锁消息通道
     */
    private String channel = "#dlock_sync";

}
