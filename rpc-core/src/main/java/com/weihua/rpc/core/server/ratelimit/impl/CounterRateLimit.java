/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-16 14:18:35
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit.impl;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 固定窗口计数器限流实现
 */
@Slf4j
public class CounterRateLimit extends AbstractRateLimiter {

    // 计数器
    private final AtomicInteger counter = new AtomicInteger(0);

    // 窗口大小(ms)，默认1秒
    private static final long WINDOW_SIZE_MS = 1000;

    // 上次重置窗口的时间
    private volatile long lastWindowResetTime;

    /**
     * 创建固定窗口计数器限流器
     * 
     * @param qps 每秒允许的最大请求数
     */
    public CounterRateLimit(int qps) {
        super(qps, Strategy.COUNTER);
        this.lastWindowResetTime = System.currentTimeMillis();
    }

    @Override
    protected boolean doTryAcquire() {
        long currentTime = System.currentTimeMillis();

        // 检查是否需要重置计数器（新的时间窗口）
        if (currentTime - lastWindowResetTime >= WINDOW_SIZE_MS) {
            synchronized (this) {
                // 双重检查，避免多线程重置冲突
                if (currentTime - lastWindowResetTime >= WINDOW_SIZE_MS) {
                    counter.set(0);
                    lastWindowResetTime = currentTime;
                    log.trace("重置固定窗口计数器: QPS={}, 时间窗口={}", qps, currentTime);
                }
            }
        }

        // 尝试增加计数器并检查是否超过限制
        int currentCount = counter.incrementAndGet();
        if (currentCount <= qps) {
            return true;
        } else {
            // 计数回退
            counter.decrementAndGet();
            log.debug("触发固定窗口限流，请求计数={}/{}", currentCount, qps);
            return false;
        }
    }

    @Override
    public void resetStatistics() {
        super.resetStatistics();
        // 同时重置固定窗口计数器
        counter.set(0);
        lastWindowResetTime = System.currentTimeMillis();
    }

    @Override
    public void updateQps(int newQps) {
        // 在更新QPS的同时，也要考虑当前窗口内的计数限制
        super.updateQps(newQps);

        // 获取当前计数
        int currentCount = counter.get();

        // 如果当前计数已经超过新的QPS限制，则重置计数器
        if (currentCount > newQps) {
            counter.set(0);
            lastWindowResetTime = System.currentTimeMillis();
            log.debug("由于QPS调低，重置固定窗口计数器: 旧计数={}, 新QPS={}", currentCount, newQps);
        }
    }
}