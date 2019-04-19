package com.github.sisyphsu.common.cluster.dlock;

import com.github.sisyphsu.common.cluster.SpringBaseTest;
import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeoutException;

/**
 * test dlock
 *
 * @author sulin
 * @since 2019-04-17 10:37:09
 */
@Slf4j
public class DistributedLockTest extends SpringBaseTest {

    @Autowired
    private DistributedLock dlock;

    @Test
    public void runInLock() throws InterruptedException {
        List<String> keyGroups = Arrays.asList(
                "a,c,d,e",
                "a,b,d",
                "b,c,d,e",
                "a,b,e",
                "c",
                "e",
                "a"
        );
        CountDownLatch latch = new CountDownLatch(keyGroups.size());
        CyclicBarrier barrier = new CyclicBarrier(keyGroups.size());
        for (final String keyGroup : keyGroups) {
            new Thread(() -> {
                try {
                    barrier.await();
                    dlock.runInLock(Arrays.asList(keyGroup.split(",")), () -> {
                        log.info("exec in lock: {} start", keyGroup);
                        ScheduleUtils.sleep(200);
                        log.info("exec in lock: {} down", keyGroup);
                    });
                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        latch.await();
    }

    @Test
    public void runInTradeLock() throws Exception {
        dlock.runInLock(Arrays.asList("user:1001", "order:100000001"), () -> {
            System.out.println("do business");
        });
    }

    @Test
    public void runInTradeLock2() throws Exception {
        List<String> keys = Arrays.asList("user:1001", "order:100000001");
        if (!dlock.lock(keys, 3000)) {
            throw new TimeoutException("lock failed");
        }
        try {
            System.out.println("do business");
        } finally {
            dlock.unlock(keys);
        }
    }

}