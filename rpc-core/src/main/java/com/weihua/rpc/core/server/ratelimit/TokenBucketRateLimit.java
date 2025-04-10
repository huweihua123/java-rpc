package com.weihua.rpc.core.server.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流实现
 */
@Slf4j
public class TokenBucketRateLimit implements RateLimit {

    // 接口名称
    private final String interfaceName;

    // 桶容量（最大令牌数）
    private final int capacity;

    // 每秒填充令牌数量（最大QPS）
    private final int refillTokensPerSecond;

    // 当前令牌数量
    private final AtomicLong currentTokens;

    // 最后一次填充时间戳（毫秒）
    private long lastRefillTimestamp;

    // 统计时间窗口（纳秒）- 默认1秒
    private static final long STATS_WINDOW_NANOS = 1_000_000_000L;

    // 请求计数和时间戳
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong requestStartNanos = new AtomicLong(System.nanoTime());

    /**
     * 构造函数
     * 
     * @param interfaceName 接口名称
     * @param maxQps        最大QPS，也是桶容量和填充速率
     */
    public TokenBucketRateLimit(String interfaceName, int maxQps) {
        this.interfaceName = interfaceName;
        this.capacity = maxQps;
        this.refillTokensPerSecond = maxQps;
        this.currentTokens = new AtomicLong(maxQps); // 初始填满桶
        this.lastRefillTimestamp = System.currentTimeMillis();

        log.info("创建令牌桶限流器: 接口={}, 最大QPS={}", interfaceName, maxQps);
    }

    @Override
    public boolean allowRequest() {
        refillTokens();

        // 尝试获取令牌
        long currentTokensValue = currentTokens.get();
        if (currentTokensValue <= 0) {
            // 没有令牌可用，限流
            log.debug("接口 {} 触发限流，当前令牌数为0", interfaceName);
            updateStats();
            return false;
        }

        // CAS更新令牌数
        boolean success = false;
        while (!success && currentTokens.get() > 0) {
            currentTokensValue = currentTokens.get();
            if (currentTokensValue > 0) {
                success = currentTokens.compareAndSet(currentTokensValue, currentTokensValue - 1);
            } else {
                break;
            }
        }

        // 更新统计信息
        updateStats();

        return success;
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        requestCount.incrementAndGet();

        // 检查是否需要重置计数器
        long currentNanos = System.nanoTime();
        long startNanos = requestStartNanos.get();

        if (currentNanos - startNanos >= STATS_WINDOW_NANOS) {
            // 尝试重置计数器
            if (requestStartNanos.compareAndSet(startNanos, currentNanos)) {
                requestCount.set(1); // 重置为1（当前请求）
            }
        }
    }

    /**
     * 填充令牌
     */
    private void refillTokens() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastRefillTimestamp;

        // 计算需要填充的令牌数
        if (elapsedTime > 0) {
            // 计算新增令牌数
            long newTokens = (elapsedTime * refillTokensPerSecond) / 1000;

            if (newTokens > 0) {
                // 更新令牌数，不超过容量
                long newTokenCount = Math.min(capacity, currentTokens.get() + newTokens);
                currentTokens.set(newTokenCount);
                lastRefillTimestamp = currentTime;

                log.trace("填充令牌: 接口={}, 新增={}, 当前={}", interfaceName, newTokens, newTokenCount);
            }
        }
    }

    @Override
    public String getInterfaceName() {
        return interfaceName;
    }

    @Override
    public int getMaxQps() {
        return capacity;
    }

    @Override
    public double getCurrentQps() {
        long currentNanos = System.nanoTime();
        long startNanos = requestStartNanos.get();
        long windowNanos = currentNanos - startNanos;

        if (windowNanos <= 0) {
            return 0;
        }

        // 计算当前QPS
        return (double) requestCount.get() * STATS_WINDOW_NANOS / windowNanos;
    }
}
