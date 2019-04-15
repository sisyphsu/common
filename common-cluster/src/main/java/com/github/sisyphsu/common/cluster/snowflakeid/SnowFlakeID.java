package com.github.sisyphsu.common.cluster.snowflakeid;


import com.github.sisyphsu.common.cluster.cid.ClusterIDImpl;
import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;

/**
 * 序号ID生成器
 *
 * @author sulin
 * @since 2019-03-22 20:40:10
 */
public class SnowFlakeID {

    private static final int DEFAULT_SEQUENCE_BIT_NUM = 6;
    private static final long BASE_TIMESTAMP = 1400000000000L;

    /**
     * 集群ID
     */
    private ClusterIDImpl clusterID;
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

    /**
     * 初始化SnowFlakeID实例
     *
     * @param clusterID 集群ID实例
     */
    public SnowFlakeID(ClusterIDImpl clusterID) {
        this(clusterID, DEFAULT_SEQUENCE_BIT_NUM);
    }

    /**
     * 初始化SnowFlakeID实例
     *
     * @param clusterID      集群ID实例
     * @param sequenceBitNum 后缀递增序号字节数
     */
    public SnowFlakeID(ClusterIDImpl clusterID, int sequenceBitNum) {
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
