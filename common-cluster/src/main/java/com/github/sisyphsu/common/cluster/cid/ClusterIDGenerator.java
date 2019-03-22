package com.github.sisyphsu.common.cluster.cid;


import java.util.function.Supplier;

/**
 * ID生成器
 * <p>
 * created by sulin at 2018-07-28 17:54:02
 */
public class ClusterIDGenerator {

    private static final Long BASE_TIMESTAMP = 1400000000000L;

    private Supplier<Integer> provider;

    private long timestamp;
    private int count;

    public ClusterIDGenerator(Supplier<Integer> provider) {
        this.provider = provider;
    }

    /**
     * 生成一个新的ID
     *
     * @return 新ID, 10进制长度15位
     */
    public synchronized long generate() {
        long timestamp = System.currentTimeMillis() - BASE_TIMESTAMP;
        long offset;
        long workId = provider.get();
        if (workId < 0 || workId > 99) {
            throw new IllegalStateException("workId invalid");
        }
        while (timestamp == this.timestamp && this.count > 99) {
            timestamp = System.currentTimeMillis() - BASE_TIMESTAMP; // 当前毫秒已耗尽, 等待下个毫秒
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
        if (timestamp != this.timestamp) {
            this.timestamp = timestamp;
            this.count = 0;
        }
        offset = count++;
        return timestamp * 10000 + workId * 100 + offset;
    }

}
