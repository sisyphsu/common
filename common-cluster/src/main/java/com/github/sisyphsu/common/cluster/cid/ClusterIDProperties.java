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
     * ID限制, 即最大值or最大数量
     */
    private int max = 255;
    /**
     * ID锁路径
     */
    private String path = "/cluster-cid";

}
