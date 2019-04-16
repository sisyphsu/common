package com.github.sisyphsu.common.cluster.tickid;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

/**
 * Wrap tickID's allocation
 *
 * @author sulin
 * @since 2019-04-15 15:43:44
 */
@Slf4j
public class TickID extends Thread {

    /**
     * batch size of pool.
     */
    private final int batch;
    /**
     * global tick provider, maybe ZooKeeper or Redis.
     */
    private final TickProvider provider;

    private boolean closed;
    private TickPool pool;
    private Semaphore semaphore;

    /**
     * Initialize
     *
     * @param provider tick provider
     * @param batch    batch size
     */
    public TickID(TickProvider provider, int batch) {
        this.batch = batch;
        this.provider = provider;
        this.semaphore = new Semaphore(1);

        this.setDaemon(true);
        this.setName("TickID-" + provider.name());
        this.start();
    }

    /**
     * generate one tickID with default timeout policy
     *
     * @return new tickID
     */
    public long generate() {
        try {
            return this.generate(Integer.MAX_VALUE); // wait...
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
        Long result = null;
        while (result == null) {
            long waitTime = endTime - System.currentTimeMillis();
            if (waitTime <= 0) {
                throw new TimeoutException("generate tickID timeout"); // timeout
            }
            synchronized (this) {
                try {
                    if (pool != null && !pool.isDrain()) {
                        result = pool.takeTickID();
                    } else {
                        pool = null;
                    }
                    if (pool == null && semaphore.availablePermits() == 0) {
                        semaphore.release(); // active loader thread
                    }
                    if (result == null) {
                        this.wait(waitTime);
                    }
                } catch (Exception e) {
                    log.warn("Error happens when generate new tickID. ", e);
                }
            }
        }
        return result;
    }

    @Override
    public void run() {
        TickPool nextPool = null;
        while (!closed) {
            // prepare new bucket of tick
            if (nextPool == null) {
                try {
                    long max = provider.acquireTick(batch);
                    long min = max - batch;
                    nextPool = new TickPool(min, max);
                } catch (Exception e) {
                    log.error("load tick error", e);
                }
            }
            // update pool in lock if need
            synchronized (this) {
                if (nextPool != null && this.pool == null) {
                    this.pool = nextPool;
                    nextPool = null;
                }
                this.notifyAll();
            }
            // wait next semaphore
            if (nextPool != null && this.pool != null) {
                try {
                    semaphore.acquire(1);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * close current TickID instance
     */
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.interrupt();
    }

}
