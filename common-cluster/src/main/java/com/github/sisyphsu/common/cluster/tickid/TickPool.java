package com.github.sisyphsu.common.cluster.tickid;

/**
 * Represent a pool of Ticks.
 *
 * @author sulin
 * @since 2019-04-15 15:33:05
 */
public class TickPool {

    private long min;
    private final long max;

    /**
     * Initialize a TickPool
     *
     * @param min min TickID, include
     * @param max max TickID, exclude
     */
    public TickPool(long min, long max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Fetch the remain tick's count
     *
     * @return Remain count
     */
    public int getTickNum() {
        return Math.max((int) (this.max - this.min), 0);
    }

    /**
     * Check this pool is drain or not.
     *
     * @return is drain
     */
    public boolean isDrain() {
        return this.getTickNum() == 0;
    }

    /**
     * Fetch one tickId in block mode.
     *
     * @return tickID
     */
    public long takeTickID() {
        if (this.max == this.min) {
            return -1;
        }
        return this.min++;
    }

}
