package com.github.sisyphsu.common.cluster.snowflakeid;


import com.github.sisyphsu.common.cluster.cid.ClusterID;
import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;

/**
 * Used for generating SnowFlakeID, which based on ClusterID
 *
 * @author sulin
 * @since 2019-03-22 20:40:10
 */
public class SnowFlakeID {

    // 2016-01-01 00:00:00 GMT+0800
    private static final long BASE_TIMESTAMP = 1454256000000L;

    private static final int TIMESTAMP_BIT_NUM = 39;
    private static final int DEFAULT_SEQUENCE_BIT_NUM = 6;

    /**
     * Provided by outside
     */
    private ClusterID clusterID;
    /**
     * Last time that call generate
     */
    private long timestamp;
    /**
     * The bit count of timestamp prefix.
     */
    private int timestampBitNum;
    /**
     * The max value of timestamp prefix
     */
    private long timestampMax;
    /**
     * The sequence number in current millisecond
     */
    private int sequence;
    /**
     * The bit count of sequence, which limit the max sequence number.
     */
    private int sequenceBitNum;
    /**
     * The max value of sequence
     */
    private int sequenceMax;

    /**
     * Initialize SnowFlakeID
     *
     * @param clusterID The instance of ClusterID
     */
    public SnowFlakeID(ClusterID clusterID) {
        this(clusterID, DEFAULT_SEQUENCE_BIT_NUM);
    }

    /**
     * Initialize SnowFlakeID
     *
     * @param clusterID      The instance of ClusterID
     * @param sequenceBitNum The bit count of sequence
     */
    public SnowFlakeID(ClusterID clusterID, int sequenceBitNum) {
        this(clusterID, TIMESTAMP_BIT_NUM, sequenceBitNum);
    }

    /**
     * Initialize SnowFlakeID
     *
     * @param clusterID       The instance of ClusterID
     * @param timestampBitNum The bit count of timestamp prefix
     * @param sequenceBitNum  The bit count of sequence
     */
    public SnowFlakeID(ClusterID clusterID, int timestampBitNum, int sequenceBitNum) {
        if (clusterID == null) {
            throw new NullPointerException("clusterID must be not-null");
        }
        if (timestampBitNum + sequenceBitNum + clusterID.getBits() > 63) {
            throw new IllegalArgumentException("SnowFlakeID's totalBitNum is bigger than 63");
        }
        this.clusterID = clusterID;
        this.timestampBitNum = timestampBitNum;
        this.sequenceBitNum = sequenceBitNum;

        this.timestampMax = 1 << timestampBitNum;
        this.sequenceMax = 1 << sequenceBitNum;
    }

    /**
     * Generate next ID
     *
     * @return new ID
     */
    public synchronized long generate() {
        long nowTimestamp = System.currentTimeMillis();
        long cid = clusterID.get();
        // try sleep for next millisecond if sequence was dried-up
        while (this.timestamp == nowTimestamp && this.sequence >= this.sequenceMax) {
            ScheduleUtils.sleep(0);
            nowTimestamp = System.currentTimeMillis();
        }
        // reset sequence if millisecond changed
        if (nowTimestamp != this.timestamp) {
            this.sequence = 0;
            this.timestamp = nowTimestamp;
        }
        long prefix = (this.timestamp - BASE_TIMESTAMP) % this.timestampMax;
        long result = prefix << clusterID.getBits() + cid;

        return result << this.sequenceBitNum + this.sequence++;
    }

}
