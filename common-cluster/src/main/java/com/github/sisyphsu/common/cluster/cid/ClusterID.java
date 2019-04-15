package com.github.sisyphsu.common.cluster.cid;

/**
 * 集群ID抽象接口
 *
 * @author sulin
 * @since 2019-04-15 13:45:11
 */
public interface ClusterID {

    /**
     * 获取当前节点在集群中的唯一ID
     *
     * @return 集群ID
     */
    int get();

    /**
     * 获取集群ID比特位数
     *
     * @return 比特位
     */
    int getBits();

    /**
     * 获取当前集群ID的状态
     *
     * @return 状态
     */
    ClusterIDStatus getStatus();

}
