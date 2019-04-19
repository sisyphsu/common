package com.github.sisyphsu.common.cluster.tickid;

import com.github.sisyphsu.common.cluster.SpringBaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * @author sulin
 * @since 2019-04-16 11:03:17
 */
@Slf4j
public class TickIDTest extends SpringBaseTest {

    private TickID tickID;
    private AtomicLong counter = new AtomicLong(100000000);

    @Autowired
    private TickTemplate template;

    @Before
    public void setUp() {
        tickID = new TickID(new TickProvider() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public long acquireTick(int count) {
                return counter.addAndGet(count);
            }
        }, 10);
    }

    @After
    public void tearDown() {
        tickID.close();
    }

    @Test
    public void generate() {
        for (int i = 0; i < 50; i++) {
            log.info("{}", tickID.generate());
        }
    }

    @Test
    public void testNormal() {
        TickID tickID = template.createTickID("user_id", 100);

        System.out.println(tickID.generate());

        tickID.close();
    }

    @Test
    public void generateBenchmark() {
        long start = System.currentTimeMillis();
        int times = 1000000;
        for (int i = 0; i < times; i++) {
            tickID.generate();
        }
        long cost = System.currentTimeMillis() - start;
        // generate 1000000 times, cost 1288 ms, 776397 qps
        System.out.printf("generate %d times, cost %d ms, %f qps \n", times, cost, times * 1000.0 / cost);
    }

}