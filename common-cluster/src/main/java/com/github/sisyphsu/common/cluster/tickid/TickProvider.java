package com.github.sisyphsu.common.cluster.tickid;

/**
 * Present TickID's Provider specification.
 *
 * @author sulin
 * @since 2019-04-15 15:47:07
 */
@FunctionalInterface
public interface TickProvider {

    /**
     * Acquire some tick with specified count. Implementation should like:
     * <code>return globalID+count</code>
     *
     * @param count tick count
     * @return Final globalID after acquire
     */
    long acquireTick(int count) throws Exception;

}
