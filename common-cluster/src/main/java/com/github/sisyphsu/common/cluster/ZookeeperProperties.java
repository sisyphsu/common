package com.github.sisyphsu.common.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ZooKeeper's configuration.
 *
 * @author sulin
 * @since 2019-03-22 12:08:22
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.zookeeper")
public class ZookeeperProperties {

    /**
     * ZK's server address
     */
    private String addr;
    /**
     * ZK's default namespace
     */
    private String namespace;
    /**
     * ZK RetryPolicy's times
     */
    private int retryTimes = 5;
    /**
     * ZK RetryPolicy's interval (milliseconds)
     */
    private int retryInterval = 2000;
    /**
     * Connection's timeout milliseconds.
     */
    private int connectionTimeoutMs = 3000;
    /**
     * ZK session's timeout milliseconds.
     */
    private int sessionTimeoutMs = 5000;
    /**
     * ZK's authorization information.
     */
    private List<AuthInfo> authInfos;

    @Data
    public static class AuthInfo {
        private String scheme;
        private String auth;
    }

}
