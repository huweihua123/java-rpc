/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-23 15:54:40
 * @LastEditors: weihua hu
 * @Description: 漏桶限流算法实现
 */
package com.weihua.rpc.core.server.ratelimit.impl;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 漏桶算法限流实现
 */
@Slf4j
public class LeakyBucketRateLimit extends AbstractRateLimiter {

    // 漏桶容量
    private final int capacity;

    // 漏水速率（每秒）
    private double leakRatePerSecond;

    // 当前水量
    private double currentWater;

    // 上次漏水时间
    private Instant lastLeakTime;

    // 锁，保护并发修改
    private final Lock lock = new ReentrantLock();

    /**
     * 创建漏桶限流器
     * 
     * @param qps      每秒允许的最大请求数
     * @param capacity 漏桶容量，默认等于QPS
     */
    public LeakyBucketRateLimit(int qps, int capacity) {
        super(qps, Strategy.LEAKY_BUCKET);
        this.capacity = capacity > 0 ? capacity : qps;
        this.leakRatePerSecond = qps; // 每秒漏水速率
        this.currentWater = 0;
        this.lastLeakTime = Instant.now();

        log.debug("创建漏桶限流器: QPS={}, 容量={}", qps, this.capacity);
    }

    /**
     * 创建漏桶限流器，容量默认等于QPS
     * 
     * @param qps 每秒允许的最大请求数
     */
    public LeakyBucketRateLimit(int qps) {
        this(qps, qps);
    }

    @Override
    protected boolean doTryAcquire() {
        lock.lock();
        try {
            // 计算漏出的水量
            Instant now = Instant.now();
            Duration elapsed = Duration.between(lastLeakTime, now);

            // 更新当前水量和时间
            double leaked = elapsed.toMillis() * (leakRatePerSecond / 1000.0);
            currentWater = Math.max(0, currentWater - leaked);
            lastLeakTime = now;

            // 判断是否可以加入请求
            if (currentWater < capacity) {
                // 可以加水
                currentWater++;
                return true;
            } else {
                // 水满，拒绝请求
                log.debug("触发漏桶限流，水量已满: {}/{}", currentWater, capacity);
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void resetStatistics() {
        super.resetStatistics();
        // 重置漏桶状态
        lock.lock();
        try {
            currentWater = 0;
            lastLeakTime = Instant.now();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateQps(int newQps) {
        lock.lock();
        try {
            super.updateQps(newQps);
            // 更新漏水速率
            this.leakRatePerSecond = newQps;

            // 如果水量超过新容量，则调整水量
            if (currentWater > newQps) {
                currentWater = newQps;
                log.debug("由于QPS调低，调整漏桶水量: 新水量={}, 新QPS={}", currentWater, newQps);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前漏桶水量
     * 
     * @return 当前水量
     */
    public double getCurrentWater() {
        lock.lock();
        try {
            // 先漏水再返回当前水量
            Instant now = Instant.now();
            Duration elapsed = Duration.between(lastLeakTime, now);

            double leaked = elapsed.toMillis() * (leakRatePerSecond / 1000.0);
            currentWater = Math.max(0, currentWater - leaked);
            lastLeakTime = now;

            return currentWater;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取漏桶容量
     * 
     * @return 漏桶容量
     */
    public int getCapacity() {
        return capacity;
    }
}