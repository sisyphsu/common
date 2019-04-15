package com.github.sisyphsu.common.cluster.tickid;

import com.github.sisyphsu.common.cluster.utils.ScheduleUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Wrap tickID's allocation
 *
 * @author sulin
 * @since 2019-04-15 15:43:44
 */
@Slf4j
public class TickID {

    /**
     * batch size of pool.
     */
    private final int batch;
    /**
     * global tick provider, maybe ZooKeeper or Redis.
     */
    private final TickProvider provider;

    private boolean loading;
    private ReadWriteLock lock;
    private Condition cond;
    private TickPool currPool;
    private TickPool nextPool;

    /**
     * Initialize
     *
     * @param provider tick provider
     * @param batch    batch size
     */
    public TickID(TickProvider provider, int batch) {
        this.batch = batch;
        this.provider = provider;
        this.lock = new ReentrantReadWriteLock();
        this.cond = this.lock.readLock().newCondition();

        this.checkLoad();
    }

    /**
     * generate one tickID with default timeout policy
     *
     * @return new tickID
     */
    public long generate() {
        try {
            return this.generate(Integer.MAX_VALUE);
        } catch (TimeoutException e) {
            throw new IllegalStateException("generate tickID failed");
        }
    }

    /**
     * generate one tickID with specified timeout
     *
     * @param timeout timeout milliseconds
     * @return new tickID
     */
    public long generate(long timeout) throws TimeoutException {
        long endTime = System.currentTimeMillis() + timeout;
        while (true) {
            long waitTime = System.currentTimeMillis() - endTime;
            if (waitTime <= 0) {
                throw new TimeoutException("generate tickID timeout"); // timeout
            }
            lock.readLock().lock();
            try {
                Long result = this.tryFetchTick();
                this.checkLoad();
                if (result != null) {
                    return result; // success
                }
                cond.await(waitTime, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("Error happens when generate new tickID. ", e);
            }
            lock.readLock().unlock();
        }
    }

    // try allocate an new tickID
    private Long tryFetchTick() {
        if ((currPool == null || currPool.isDrain()) && nextPool != null) {
            currPool = nextPool;
            nextPool = null;
        }
        if (currPool != null && !currPool.isDrain()) {
            return currPool.takeTickID();
        }
        return null;
    }

    // check whether need load next batch tickID or not
    private void checkLoad() {
        if (this.loading) {
            return;
        }
        if (this.currPool != null && this.nextPool != null) {
            return;
        }
        this.loading = true;
        ScheduleUtils.runAfter(0, this::execLoad);
    }

    // load tick asynchorized
    private void execLoad() {
        TickPool pool = null;
        try {
            long max = provider.acquireTick(batch);
            long min = max - batch;
            pool = new TickPool(min, max);
        } catch (Exception e) {
            log.error("load tick error", e);
        }
        // update pool in write-lock
        lock.writeLock().lock();
        try {
            if (pool != null) {
                if (this.currPool == null) {
                    this.currPool = pool;
                } else {
                    this.nextPool = pool;
                }
                this.cond.signalAll();
            }
        } finally {
            lock.writeLock().unlock();
            loading = false;
        }
    }

}
