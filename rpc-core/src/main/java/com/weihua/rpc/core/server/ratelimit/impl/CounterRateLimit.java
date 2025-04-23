package com.weihua.rpc.core.server.ratelimit.impl;
import com.weihua.rpc.core.server.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 固定窗口计数器限流实现
 */
@Slf4j
public class CounterRateLimit extends AbstractRateLimiter {

    // 计数器
    private final AtomicInteger counter = new AtomicInteger(0);

    // 窗口大小，默认1秒
    private static final Duration WINDOW_SIZE = Duration.ofSeconds(1);

    // 上次重置窗口的时间
    private volatile Instant lastWindowResetTime;

    /**
     * 创建固定窗口计数器限流器
     * 
     * @param qps 每秒允许的最大请求数
     */
    public CounterRateLimit(int qps) {
        super(qps, RateLimit.Strategy.COUNTER);
        this.lastWindowResetTime = Instant.now();
    }

    @Override
    protected boolean doTryAcquire() {
        Instant currentTime = Instant.now();

        // 检查是否需要重置计数器（新的时间窗口）
        if (Duration.between(lastWindowResetTime, currentTime).compareTo(WINDOW_SIZE) >= 0) {
            synchronized (this) {
                // 双重检查，避免多线程重置冲突
                if (Duration.between(lastWindowResetTime, currentTime).compareTo(WINDOW_SIZE) >= 0) {
                    counter.set(0);
                    lastWindowResetTime = currentTime;
                    log.trace("重置固定窗口计数器: QPS={}, 时间窗口={}", qps, currentTime);
                }
            }
        }

        // 其余逻辑保持不变
        int currentCount = counter.incrementAndGet();
        if (currentCount <= qps) {
            return true;
        } else {
            counter.decrementAndGet();
            log.debug("触发固定窗口限流，请求计数={}/{}", currentCount, qps);
            return false;
        }
    }

    @Override
    public void resetStatistics() {
        super.resetStatistics();
        counter.set(0);
        lastWindowResetTime = Instant.now();
    }

    // updateQps方法逻辑保持不变，只需修改时间部分
    @Override
    public void updateQps(int newQps) {
        super.updateQps(newQps);
        int currentCount = counter.get();
        if (currentCount > newQps) {
            counter.set(0);
            lastWindowResetTime = Instant.now();
            log.debug("由于QPS调低，重置固定窗口计数器: 旧计数={}, 新QPS={}", currentCount, newQps);
        }
    }
}