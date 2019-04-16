package com.github.sisyphsu.common.cluster.cid;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ClusterID's configuration
 *
 * @author sulin
 * @since 2019-03-22 12:00:18
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.cluster")
public class ClusterIDProperties {

    /**
     * id's bit count, used for min and max value limit, default 8 (1<<8=256).
     */
    private int bitNum = 8;
    /**
     * the lock path in zookeeper
     */
    private String path = "/clusterid";

}
