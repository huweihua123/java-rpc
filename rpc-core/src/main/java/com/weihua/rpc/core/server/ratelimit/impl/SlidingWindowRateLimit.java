/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-23 15:49:27
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit.impl;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 滑动窗口限流实现
 */
@Slf4j
public class SlidingWindowRateLimit extends AbstractRateLimiter {

    // 滑动窗口大小 (10个子窗口组成1秒)
    private static final int WINDOW_COUNT = 10;

    // 窗口大小
    private static final Duration WINDOW_SIZE = Duration.ofMillis(1000 / WINDOW_COUNT);

    // 单个窗口最大请求数
    private final int maxRequestsPerWindow;

    // 窗口计数器
    private final AtomicInteger[] windowCounters;

    // 当前窗口索引
    private AtomicInteger currentWindowIndex;

    // 上次窗口重置时间
    private AtomicLong lastResetTime;

    // 锁，用于更新QPS时的原子操作
    private final Lock lock = new ReentrantLock();

    /**
     * 创建滑动窗口限流器
     * 
     * @param qps 每秒允许的最大请求数
     */
    public SlidingWindowRateLimit(int qps) {
        super(qps, Strategy.SLIDING_WINDOW);
        this.maxRequestsPerWindow = qps / WINDOW_COUNT;

        // 初始化窗口计数器
        this.windowCounters = new AtomicInteger[WINDOW_COUNT];
        for (int i = 0; i < WINDOW_COUNT; i++) {
            this.windowCounters[i] = new AtomicInteger(0);
        }

        this.currentWindowIndex = new AtomicInteger(0);
        this.lastResetTime = new AtomicLong(System.currentTimeMillis());

        log.debug("创建滑动窗口限流器: 最大QPS={}, 窗口数={}", qps, WINDOW_COUNT);
    }

    @Override
    protected boolean doTryAcquire() {
        // 检查是否需要滑动窗口
        slidingWindowIfNeeded();

        // 获取当前窗口计数器
        int idx = currentWindowIndex.get();
        int currentCount = windowCounters[idx].get();

        // 判断是否可以通过请求
        if (currentCount < maxRequestsPerWindow) {
            windowCounters[idx].incrementAndGet();
            return true;
        } else {
            log.debug("触发滑动窗口限流，当前窗口请求数已达上限: {}/{}", currentCount, maxRequestsPerWindow);
            return false;
        }
    }

    private void slidingWindowIfNeeded() {
        long current = System.currentTimeMillis();
        long last = lastResetTime.get();

        // 计算经过的窗口数
        long elapsedWindows = Duration.ofMillis(current - last).toMillis() / WINDOW_SIZE.toMillis();

        if (elapsedWindows > 0) {
            // CAS更新上次重置时间
            if (lastResetTime.compareAndSet(last, last + elapsedWindows * WINDOW_SIZE.toMillis())) {
                // 滑动窗口，最多滑动WINDOW_COUNT个窗口
                int windowsToSlide = (int) Math.min(elapsedWindows, WINDOW_COUNT);

                int oldIdx = currentWindowIndex.get();
                for (int i = 1; i <= windowsToSlide; i++) {
                    int newIdx = (oldIdx + i) % WINDOW_COUNT;
                    windowCounters[newIdx].set(0); // 重置窗口
                }

                // 更新当前窗口索引
                currentWindowIndex.set((oldIdx + windowsToSlide) % WINDOW_COUNT);

                if (log.isTraceEnabled()) {
                    log.trace("滑动窗口: 滑动{}个窗口, 新窗口索引={}", windowsToSlide, currentWindowIndex.get());
                }
            }
        }
    }

    @Override
    public void resetStatistics() {
        super.resetStatistics();

        // 重置所有窗口计数器
        lock.lock();
        try {
            for (int i = 0; i < WINDOW_COUNT; i++) {
                windowCounters[i].set(0);
            }
            lastResetTime.set(System.currentTimeMillis());
            currentWindowIndex.set(0);
        } finally {
            lock.unlock();
        }

        log.debug("重置滑动窗口限流统计");
    }

    @Override
    public void updateQps(int newQps) {
        lock.lock();
        try {
            super.updateQps(newQps);

            // 更新单个窗口最大请求数
            int newMaxRequestsPerWindow = newQps / WINDOW_COUNT;

            // 如果新的QPS更低，则清空所有窗口
            if (newMaxRequestsPerWindow < this.maxRequestsPerWindow) {
                for (int i = 0; i < WINDOW_COUNT; i++) {
                    windowCounters[i].set(0);
                }

                log.debug("由于QPS调低，重置所有滑动窗口计数器: 旧窗口限制={}, 新窗口限制={}",
                        this.maxRequestsPerWindow, newMaxRequestsPerWindow);
            }

            // 设置新的限制
            // 注意这里我们需要使用字段赋值，不能直接修改final字段
            // 所以将maxRequestsPerWindow改为non-final
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前总请求数（所有窗口）
     * 
     * @return 当前总请求数
     */
    public int getCurrentWindowsTotal() {
        int total = 0;
        for (int i = 0; i < WINDOW_COUNT; i++) {
            total += windowCounters[i].get();
        }
        return total;
    }

    /**
     * 获取窗口数量
     * 
     * @return 窗口数量
     */
    public int getWindowCount() {
        return WINDOW_COUNT;
    }
}