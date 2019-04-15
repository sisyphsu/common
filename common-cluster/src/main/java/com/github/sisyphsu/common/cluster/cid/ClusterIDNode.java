package com.github.sisyphsu.common.cluster.cid;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * the zookeeper lock model
 *
 * @author sulin
 * @since 2019-03-22 12:13:00
 */
@Data
@AllArgsConstructor
public class ClusterIDNode {

    /**
     * ClusterID's value
     */
    private int id;
    /**
     * ClusterID's last active time
     */
    private long timestamp;
    /**
     * Whether locked or not
     */
    private boolean locked;

}
