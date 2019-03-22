package com.github.sisyphsu.common.cluster.cid;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集群配置
 *
 * @author sulin
 * @since 2019-03-22 12:00:18
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.cluster")
public class ClusterIDProperties {

    /**
     * ID比特位限制, 用于限制最大值or最大数量
     * 8->256
     */
    private int bits = 5;
    /**
     * ID锁路径
     */
    private String path = "/cluster-cid";

}
