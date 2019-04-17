package com.github.sisyphsu.common.cluster.cid;

import com.github.sisyphsu.common.cluster.SpringBaseTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * test clusterID
 *
 * @author sulin
 * @since 2019-04-17 10:38:04
 */
@Slf4j
public class ClusterIDTest extends SpringBaseTest {

    @Autowired
    private ClusterID clusterID;
    @Autowired
    private CuratorFramework curatorFramework;
    @Autowired
    private ClusterIDProperties properties;

    @Test
    public void testID() {
        log.info("clusterID: {}", clusterID.get());
    }

    @Test
    public void testMulti() {
        properties.setBitNum(4);
        for (int i = 0; i < 16; i++) {
            ClusterID id = new ClusterIDImpl(curatorFramework, properties);
            log.info("id: {}", id.get());
        }

        log.info("### test block");
        ClusterID id = new ClusterIDImpl(curatorFramework, properties);
        log.info("id: {}", id.get());
    }

}