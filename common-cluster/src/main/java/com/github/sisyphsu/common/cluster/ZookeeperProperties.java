package com.github.sisyphsu.common.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ZK配置
 *
 * @author sulin
 * @since 2019-03-22 12:08:22
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.zookeeper")
public class ZookeeperProperties {

    /**
     * ZK服务地址
     */
    private String addr;
    /**
     * ZK命名空间
     */
    private String namespace;
    /**
     * 失败重试次数
     */
    private int retryTimes = 5;
    /**
     * 失败重试时间间隔
     */
    private int retryInterval = 2000;
    /**
     * 连接超时时间
     */
    private int connectionTimeoutMs = 3000;
    /**
     * 会话超时时间
     */
    private int sessionTimeoutMs = 5000;
    /**
     * 认证信息
     */
    private List<AuthInfo> authInfos;

    /**
     * 认证信息
     */
    @Data
    public static class AuthInfo {
        private String scheme;
        private String auth;
    }

}
