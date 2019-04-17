package com.github.sisyphsu.common.cluster.dlock;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.*;

/**
 * test dlock
 *
 * @author sulin
 * @since 2019-04-17 10:37:09
 */
public class DistributedLockTest {

    @Autowired
    private DistributedLock dlock;

    @Test
    public void runInLock() {

    }

}