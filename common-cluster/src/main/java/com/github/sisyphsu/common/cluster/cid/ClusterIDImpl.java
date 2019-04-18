package com.github.sisyphsu.common.cluster.cid;

import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.zookeeper.CreateMode;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ClusterID implementation.
 * Based on zookeeper, provide clusterId's allocation and competition.
 *
 * @author sulin
 * @since 2019-03-22 12:08:36
 */
@Slf4j
public class ClusterIDImpl extends Thread implements ClusterID {

    private final CuratorFramework curator;
    private final ClusterIDProperties props;

    /**
     * ClusterID's status
     */
    private ClusterIDStatus status = ClusterIDStatus.NONE;
    /**
     * Status's semaphore, will be released when status changed
     */
    private Semaphore statusSema = new Semaphore(0);
    /**
     * The current ClusterID value, -1 means invalid
     */
    private int nodeID = -1;
    /**
     * ZK's node lock, use it to occupy one specified id
     */
    private InterProcessMutex nodeLock;
    /**
     * Whether closed or not
     */
    private boolean closed = false;

    /**
     * Initialize and start a daemon thread to occupy nodelock
     *
     * @param curator ZK's curator instance
     * @param props   ClusterID's configuration
     */
    public ClusterIDImpl(CuratorFramework curator, ClusterIDProperties props) {
        Assert.notNull(curator, "curator can't be null");
        Assert.notNull(props, "props can't be null");

        this.curator = curator;
        this.props = props;

        this.setDaemon(true);
        this.setName("ClusterID");
        this.start();
    }

    @Override
    public int getBitNum() {
        return this.props.getBitNum();
    }

    @Override
    public int get() {
        while (status == ClusterIDStatus.NONE && !closed) {
            try {
                statusSema.acquire();
            } catch (InterruptedException ignored) {
            }
        }
        if (closed) {
            throw new IllegalStateException("ClusterID is closed.");
        }
        return this.nodeID;
    }

    @Override
    public ClusterIDStatus getStatus() {
        return this.status;
    }

    @Override
    public void run() {
        ConnectionStateListener listener = (client, newState) -> {
            if (newState == ConnectionState.LOST) {
                this.updateStatus(ClusterIDStatus.UNLOCK, nodeID);
            }
        };
        this.curator.getConnectionStateListenable().addListener(listener);
        while (!this.closed) {
            switch (status) {
                case NONE:
                    try {
                        Integer nodeID = this.allocateNodeID();
                        if (nodeID == null) {
                            log.warn("No available nodeID for {}, retry later", props.getPath());
                        } else {
                            this.flushTimestamp(nodeID); // flush timestamp before confirm nodeId
                            this.updateStatus(ClusterIDStatus.LOCK, nodeID); // alloc new id
                        }
                    } catch (Exception e) {
                        log.error("Allocate ClusterID failed.", e);
                    }
                    ScheduleUtils.sleep(1000);
                    break;
                case LOCK:
                    try {
                        this.flushTimestamp(nodeID);
                    } catch (Exception e) {
                        log.error("flush node's time failed.", e);
                    }
                    ScheduleUtils.sleep(20000);
                    break;
                case UNLOCK:
                    try {
                        if (this.tryLockNode(nodeID)) {
                            log.debug("relock the old nodeID[{}] after reconnection.", nodeID);
                            this.updateStatus(ClusterIDStatus.LOCK, nodeID);
                        } else {
                            log.warn("relock the old nodeID[{}] failed! there will be some risks in the past.", nodeID);
                            this.updateStatus(ClusterIDStatus.NONE, -1);
                        }
                    } catch (Exception e) {
                        log.error("relock the old nodeID failed.", e);
                    }
                    ScheduleUtils.sleep(5000);
                    break;
            }
        }
        this.curator.getConnectionStateListenable().removeListener(listener);
    }

    // allocate an new avaliable nodeID
    private Integer allocateNodeID() throws Exception {
        List<ClusterIDNode> nodes = this.listNodes();
        Integer nodeID = null;
        int maxID = 1 << props.getBitNum();
        if (nodes.size() < maxID) {
            Set<Integer> nodeIds = nodes.stream().map(ClusterIDNode::getId).collect(Collectors.toSet());
            for (int i = 0; i < maxID; i++) {
                if (!nodeIds.contains(i)) {
                    log.debug("take over an new nodeID: {}", i);
                    nodeID = i;
                    break;
                }
            }
        } else {
            nodes.removeIf(node -> node.isLocked() || node.getId() >= maxID);
            if (nodes.isEmpty()) {
                log.warn("no available nodeID");
                return null;
            }
            Long minTimestamp = nodes.stream().map(ClusterIDNode::getTimestamp).min(Long::compareTo).get();
            for (ClusterIDNode node : nodes) {
                if (node.getTimestamp() == minTimestamp) {
                    log.debug("take over an old nodeID: {}", node.getId());
                    nodeID = node.getId();
                    break;
                }
            }
        }
        if (nodeID != null && !tryLockNode(nodeID)) {
            log.debug("lock the nodeID failed.");
            nodeID = null;
        }
        return nodeID;
    }

    // try lock the specified nodeID
    private boolean tryLockNode(int nodeID) throws Exception {
        if (this.nodeLock != null) {
            try {
                this.nodeLock.release();
            } catch (Exception e) {
                log.error("release old nodeLock failed.", e.getLocalizedMessage());
            }
        }
        this.nodeLock = new InterProcessMutex(curator, props.getPath() + "/" + nodeID);
        return this.nodeLock.acquire(1, TimeUnit.SECONDS);
    }

    // query all used nodeID, no matter it's locked or not.
    private List<ClusterIDNode> listNodes() throws Exception {
        if (curator.checkExists().forPath(props.getPath()) == null) {
            curator.create().creatingParentsIfNeeded().forPath(props.getPath());
        }
        List<ClusterIDNode> result = new ArrayList<>();
        for (String id : curator.getChildren().forPath(props.getPath())) {
            String fullpath = props.getPath() + "/" + id;
            String timestamp = new String(curator.getData().forPath(fullpath));
            if (!NumberUtils.isDigits(id) || !NumberUtils.isDigits(timestamp)) {
                log.warn("invalid node: {}, {}", id, timestamp);
                continue;
            }
            int nodeID = NumberUtils.toInt(id);
            long nodeTimestamp = NumberUtils.toLong(timestamp);
            boolean locked = !CollectionUtils.isEmpty(curator.getChildren().forPath(fullpath));
            result.add(new ClusterIDNode(nodeID, nodeTimestamp, locked));
        }
        return result;
    }

    // flush the specified nodeID's timestamp
    private void flushTimestamp(int nodeID) throws Exception {
        String path = props.getPath() + "/" + nodeID;
        if (curator.getState() != CuratorFrameworkState.STARTED) {
            return;
        }
        if (curator.checkExists().forPath(path) == null) {
            curator.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path);
        }
        curator.setData().forPath(path, String.valueOf(System.currentTimeMillis()).getBytes());
    }

    // update the current ClusterID's status
    private void updateStatus(ClusterIDStatus status, int nodeID) {
        log.info("ClusterID changed: {}, {}", status, nodeID);
        this.nodeID = nodeID;
        this.status = status;
        this.statusSema.release();
    }

}
