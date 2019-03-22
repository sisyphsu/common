package com.github.sisyphsu.common.cluster.cid;

import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.zookeeper.CreateMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 集群ID分配器, 通过ZK实现集群ID的分配, 但并不强依赖ZK的可靠性.
 *
 * @author sulin
 * @since 2019-03-22 12:08:36
 */
@Slf4j
public class ClusterID extends Thread {

    private final CuratorFramework curator;
    private final ClusterIDProperties props;

    /**
     * 集群ID分配状态
     */
    private ClusterIDStatus status = ClusterIDStatus.NONE;
    /**
     * 状态信号量
     */
    private Semaphore statusSema = new Semaphore(0);
    /**
     * 节点ID
     */
    private int nodeID = -1;
    /**
     * 节点锁, 通过锁占用ID
     */
    private InterProcessMutex nodeLock;
    /**
     * 是否已关闭
     */
    private boolean closed = false;

    /**
     * 初始化集群ID, 同步地占用一个ID
     * 如果ID已全部被占用, 则抛出异常中断服务
     *
     * @param curator ZK客户端框架
     * @param props   集群ID配置
     */
    public ClusterID(CuratorFramework curator, ClusterIDProperties props) {
        this.curator = curator;
        this.props = props;

        this.setDaemon(true);
        this.setName("CLUSTER-ID");
        this.start();
    }

    /**
     * 获取当前JVM的集群ID
     *
     * @return 集群ID
     */
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
                            this.flushTimestamp(nodeID); // 确定ID之前先刷新一次时间戳
                            this.updateStatus(ClusterIDStatus.LOCK, nodeID); // 分配新ID
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
                    ScheduleUtils.sleep(30000);
                    break;
                case UNLOCK:
                    try {
                        if (this.tryLockNode(nodeID)) {
                            this.updateStatus(ClusterIDStatus.LOCK, nodeID); // ZK重连后继续锁定旧ID
                        } else {
                            log.warn("dlock old nodeID[{}] failed!!!", nodeID);
                            this.updateStatus(ClusterIDStatus.NONE, -1); // 当前ID被抢占, 可能导致集群在过去一段时间内出现重复ID
                        }
                    } catch (Exception e) {
                        log.error("Relock node failed.", e);
                    }
                    ScheduleUtils.sleep(5000);
                    break;
            }
        }
        this.curator.getConnectionStateListenable().removeListener(listener);
    }

    // 分配一个新的可用ID
    private Integer allocateNodeID() throws Exception {
        List<ClusterIDNode> nodes = this.listNodes();
        Integer nodeID = null;
        if (nodes.size() < props.getMax()) {
            Set<Integer> nodeIds = nodes.stream().map(ClusterIDNode::getId).collect(Collectors.toSet());
            for (int i = 0; i < props.getMax(); i++) {
                if (!nodeIds.contains(i)) {
                    nodeID = i; // 干净的新ID
                    break;
                }
            }
        } else {
            nodes.removeIf(ClusterIDNode::isLocked);
            if (nodes.isEmpty()) {
                log.warn("no available nodeID");
                return null;
            }
            Long minTimestamp = nodes.stream().map(ClusterIDNode::getTimestamp).min(Long::compareTo).get();
            for (ClusterIDNode node : nodes) {
                if (node.getTimestamp() == minTimestamp) {
                    log.info("use old nodeID: {}", node.getId());
                    nodeID = node.getId();// 旧ID被征用
                    break;
                }
            }
        }
        if (nodeID != null && !tryLockNode(nodeID)) {
            nodeID = null; // 占用失败
        }
        return nodeID;
    }

    // 尝试锁定指定节点ID
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

    // 查询全部节点
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
            boolean locked = CollectionUtils.isNotEmpty(curator.getChildren().forPath(fullpath));
            result.add(new ClusterIDNode(nodeID, nodeTimestamp, locked));
        }
        return result;
    }

    // 刷新当前节点时间戳
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

    // 更新状态
    private void updateStatus(ClusterIDStatus status, int nodeID) {
        log.warn("ClusterID changed: {}, {}", status, nodeID);
        this.nodeID = nodeID;
        this.status = status;
        this.statusSema.release();
    }

}
