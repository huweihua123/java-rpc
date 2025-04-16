/*
 * @Author: weihua hu
 * @Date: 2025-04-15 16:45:00
 * @LastEditTime: 2025-04-16 14:06:52
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流器抽象基类，提供通用的统计逻辑和SPI扩展支持
 */
@Slf4j
public abstract class AbstractRateLimiter implements RateLimit {

    @Getter
    protected int qps;

    @Getter
    protected Strategy strategy;

    // 请求计数器
    protected final AtomicLong requestCounter = new AtomicLong(0);

    // 拒绝计数器
    protected final AtomicLong rejectCounter = new AtomicLong(0);

    // 最后一次重置统计的时间戳
    protected volatile long lastResetTime = System.currentTimeMillis();

    public AbstractRateLimiter(int qps, Strategy strategy) {
        this.qps = qps;
        this.strategy = strategy;
        log.debug("创建限流器: 策略={}, QPS={}", strategy, qps);
    }

    @Override
    public boolean tryAcquire() {
        requestCounter.incrementAndGet();
        boolean result = doTryAcquire();
        if (!result) {
            rejectCounter.incrementAndGet();
        }
        return result;
    }

    /**
     * 具体的限流算法实现
     * 
     * @return 是否允许通过
     */
    protected abstract boolean doTryAcquire();

    @Override
    public long getRequestCount() {
        return requestCounter.get();
    }

    @Override
    public long getRejectCount() {
        return rejectCounter.get();
    }

    @Override
    public void resetStatistics() {
        requestCounter.set(0);
        rejectCounter.set(0);
        lastResetTime = System.currentTimeMillis();
        log.debug("重置限流器统计: 策略={}, QPS={}", strategy, qps);
    }

    @Override
    public void updateQps(int newQps) {
        this.qps = newQps;
        log.debug("更新限流器QPS: 策略={}, 新QPS={}", strategy, newQps);
    }
}