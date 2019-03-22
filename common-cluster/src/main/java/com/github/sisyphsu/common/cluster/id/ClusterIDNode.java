package com.github.sisyphsu.common.cluster.id;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 节点模型
 *
 * @author sulin
 * @since 2019-03-22 12:13:00
 */
@Data
@AllArgsConstructor
public class ClusterIDNode {

    /**
     * 节点的集群ID
     */
    private int id;
    /**
     * 时间戳
     */
    private long timestamp;
    /**
     * 是否锁定
     */
    private boolean locked;

}
