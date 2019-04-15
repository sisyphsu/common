package com.github.sisyphsu.common.cluster.cid;

/**
 * ClusterID specification.
 *
 * @author sulin
 * @since 2019-04-15 13:45:11
 */
public interface ClusterID {

    /**
     * Get the unique ID of the current node in the cluster.
     * Will block if not ready.
     *
     * @return clusterID value
     */
    int get();

    /**
     * get the current ClusterID's bit count
     *
     * @return bit count
     */
    int getBitNum();

    /**
     * get the current ClusterID's status
     *
     * @return status
     */
    ClusterIDStatus getStatus();

}
