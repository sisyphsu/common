package com.github.sisyphsu.common.cluster.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Wrap some util methods for Thread schedule.
 *
 * @author sulin
 * @since 2019-03-22 10:19:54
 */
@Slf4j
public class ScheduleUtils {

    private static final ScheduledExecutorService EXECUTOR;

    static {
        AtomicInteger threadIdx = new AtomicInteger(0);
        EXECUTOR = new ScheduledThreadPoolExecutor(16, run -> {
            Thread thread = new Thread(run);
            thread.setName("schedule-utils-pool-" + threadIdx.incrementAndGet());
            return thread;
        });
    }

    /**
     * Run specified function every `ms` milliseconds.
     *
     * @param ms   interval
     * @param exec execute body
     * @return Future
     */
    public static Future runEvery(int ms, Runnable exec) {
        return EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                exec.run();
            } catch (Exception e) {
                log.error("runEvery failed: ", e);
            }
        }, 1, ms, MILLISECONDS);
    }

    /**
     * Run specified function after `ms` milliseconds.
     *
     * @param ms   delay time
     * @param exec execute body
     * @return Future
     */
    public static Future runAfter(int ms, Runnable exec) {
        return EXECUTOR.schedule(() -> {
            try {
                exec.run();
            } catch (Exception e) {
                log.error("runAfter failed: ", e);
            }
        }, ms, MILLISECONDS);
    }

    /**
     * Run specified function infinity until it was cancelled.
     *
     * @param exec execute body
     * @return Future
     */
    public static Future runInfinity(Runnable exec) {
        return runInfinity(exec, 1);
    }

    /**
     * Run specified function infinity until it was cancelled.
     *
     * @param exec     execute body
     * @param interval interval milliseconds
     * @return Future
     */
    public static Future runInfinity(Runnable exec, long interval) {
        Future future = new CompletableFuture();
        Thread thread = new Thread(() -> {
            while (!future.isCancelled()) {
                try {
                    exec.run();
                } catch (Exception e) {
                    log.error("runInfinity failed. ", e);
                }
                sleep(interval);
            }
        });
        thread.setName("Infinity-" + exec);
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    /**
     * Sleep `ms` milliseconds seliently, and swallow InterruptedException.
     *
     * @param ms sleep milliseconds
     */
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Run specified function seliently
     *
     * @param run execute body
     */
    public static void runSeliently(RunnableWithException run) {
        try {
            run.exec();
        } catch (Exception ignored) {
        }
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void exec() throws Exception;
    }

}
