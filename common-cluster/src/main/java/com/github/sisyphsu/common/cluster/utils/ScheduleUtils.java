package com.github.sisyphsu.common.cluster.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * 调度工具类
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
     * 每个若干毫秒执行一次指定函数
     *
     * @param ms   执行间隔
     * @param exec 执行函数
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
     * 稍后执行指定函数
     *
     * @param ms   延迟时间(毫秒)
     * @param exec 执行函数
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
     * 启动独立的线程循环执行exec, 直至撤销
     *
     * @param exec 执行单元
     * @return Future
     */
    public static Future runInfinity(Runnable exec) {
        return runInfinity(exec, 1);
    }

    /**
     * 启动独立的线程以固定的时间间隔循环执行exec, 直至撤销
     *
     * @param exec     执行单元
     * @param interval 执行间隔
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
     * 让当前线程睡眠一段时间, 并忽略InterruptedException
     *
     * @param ms 睡眠时间
     */
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * 安静地执行某些客户忽略异常的方法
     *
     * @param run 待执行方法
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
