package com.github.sisyphsu.common.cluster.cid;

/**
 * clusterID specification.
 * by default, it will return an old nodeId even if zookeeper was done.
 * if your service is sensitive to the ZooKeeper status, you can call `getStatus` before use it.
 *
 * @author sulin
 * @since 2019-04-15 13:45:11
 */
public interface ClusterID {

    /**
     * get the current ClusterID's bit count
     *
     * @return bit count
     */
    int getBitNum();

    /**
     * Get the unique ID of the current node in the cluster, which will block if not ready.
     *
     * @return clusterID value
     */
    int get();

    /**
     * get the current ClusterID's status
     *
     * @return status
     */
    ClusterIDStatus getStatus();

}
