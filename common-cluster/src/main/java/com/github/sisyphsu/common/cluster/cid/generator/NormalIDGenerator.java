package com.github.sisyphsu.common.cluster.cid.generator;


import com.github.sisyphsu.common.cluster.cid.ClusterID;
import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;

/**
 * 序号ID生成器
 *
 * @author sulin
 * @since 2019-03-22 20:40:10
 */
public class NormalIDGenerator {

    private static final Long BASE_TIMESTAMP = 1400000000000L;

    /**
     * 集群ID
     */
    private ClusterID clusterID;
    /**
     * 当前时间戳
     */
    private long timestamp;
    /**
     * 递增序号的bit位数量, 即每毫秒取若干个递增bit
     */
    private int sequenceBitNum;
    /**
     * 当前已使用序号
     */
    private int sequence;

    public NormalIDGenerator(ClusterID clusterID, int sequenceBitNum) {
        this.clusterID = clusterID;
        this.sequenceBitNum = sequenceBitNum;
    }

    /**
     * 生成一个新的ID
     *
     * @return 新ID
     */
    public synchronized long generate() {
        long currTime = System.currentTimeMillis();
        long nodeID = clusterID.get();
        int maxSequence = 2 << this.sequenceBitNum;
        while (currTime == this.timestamp && this.sequence >= maxSequence) {
            ScheduleUtils.sleep(0); // 当前毫秒已耗尽, 等待下个毫秒
            currTime = System.currentTimeMillis();
        }
        if (currTime != this.timestamp) {
            this.sequence = 0;
            this.timestamp = currTime;
        }
        long suffix = nodeID << this.sequenceBitNum + this.sequence++;
        long prefix = this.generatePrefix(currTime) << (this.sequenceBitNum + clusterID.getBits());
        return prefix + suffix;
    }

    protected long generatePrefix(long timestamp) {
        return timestamp - BASE_TIMESTAMP;
    }

}
