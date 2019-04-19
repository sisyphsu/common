package com.github.sisyphsu.common.cluster.snowflakeid;

import com.github.sisyphsu.common.cluster.cid.ClusterID;
import com.github.sisyphsu.common.cluster.cid.ClusterIDStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

/**
 * Test SnowFlakeID
 *
 * @author sulin
 * @since 2019-04-16 10:03:18
 */
@Slf4j
public class SnowFlakeIDTest {

    private SnowFlakeID flakeID;

    @Before
    public void setUp() throws Exception {
        flakeID = new SnowFlakeID(new ClusterID() {
            @Override
            public int getBitNum() {
                return 8;
            }

            @Override
            public int get() {
                return 1;
            }

            @Override
            public ClusterIDStatus getStatus() {
                return ClusterIDStatus.LOCK;
            }
        }, 6);
    }

    @Test
    public void testOne() {
        int timestampBitNum = 40;
        int nodeBitNum = 10;
        int sequenceBitNum = 8;
        SnowFlakeID flakeID = new SnowFlakeID(new ClusterID() {
            @Override
            public int getBitNum() {
                return nodeBitNum;
            }

            @Override
            public int get() {
                return 1;
            }

            @Override
            public ClusterIDStatus getStatus() {
                return ClusterIDStatus.LOCK;
            }
        }, timestampBitNum, sequenceBitNum);

        System.out.println(flakeID.generate());
    }

    @Test
    public void testSimple() {
        for (int i = 0; i < 5; i++) {
            long id = flakeID.generate();
            System.out.println(id);
            System.out.println(Long.toBinaryString(id));
        }
    }

    @Test
    public void testPerformance() {
        long start = System.currentTimeMillis();
        int times = 1000000;
        for (int i = 0; i < times; i++) {
            flakeID.generate();
        }
        long cost = System.currentTimeMillis() - start;
        // 6w qps
        System.out.printf("exec %d times, cost %d ms: %f qps", times, cost, times * 1000.0 / cost);
    }

}
