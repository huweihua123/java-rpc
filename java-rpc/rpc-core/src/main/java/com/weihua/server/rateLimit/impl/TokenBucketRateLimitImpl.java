package com.weihua.server.rateLimit.impl;

import com.weihua.server.rateLimit.RateLimit;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TokenBucketRateLimitImpl implements RateLimit {
    // 令牌产生间隔（毫秒）
    private volatile int rateMs;
    // 桶的容量
    private volatile int capacity;
    // 当前令牌数量
    private volatile int currentTokens;
    // 上次令牌发放时间
    private volatile long lastRefillTime;
    // 是否已初始化
    private volatile boolean initialized = false;

    public TokenBucketRateLimitImpl(int rateMs, int capacity) {
        updateConfig(rateMs, capacity);
    }

    /**
     * 更新限流器配置
     * 
     * @param rateMs   令牌产生间隔（毫秒）
     * @param capacity 桶的容量
     */
    public synchronized void updateConfig(int rateMs, int capacity) {
        // 参数有效性检查
        if (rateMs <= 0) {
            log.warn("令牌产生间隔必须大于0，使用默认值5ms");
            rateMs = 5;
        }

        if (capacity <= 0) {
            log.warn("令牌桶容量必须大于0，使用默认值100");
            capacity = 100;
        }

        this.rateMs = rateMs;
        this.capacity = capacity;

        // 初始化时填满令牌桶，更新时保持当前令牌与新容量的比例
        if (!initialized) {
            this.currentTokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
            this.initialized = true;
        } else {
            // 如果容量变大，按比例增加当前令牌
            if (capacity > this.capacity) {
                double ratio = (double) this.currentTokens / this.capacity;
                this.currentTokens = (int) (ratio * capacity);
            } else if (currentTokens > capacity) {
                // 如果容量变小，且当前令牌超过新容量，则截断
                this.currentTokens = capacity;
            }
            // 保持lastRefillTime不变
        }
    }

    @Override
    public synchronized boolean getToken() {
        long now = System.currentTimeMillis();
        // 计算需要补充的令牌数
        long elapsedTime = now - lastRefillTime;
        int newTokens = (int) (elapsedTime / rateMs);

        if (newTokens > 0) {
            currentTokens = Math.min(capacity, currentTokens + newTokens);
            lastRefillTime = now - (elapsedTime % rateMs); // 更精确的时间记录
        }

        if (currentTokens > 0) {
            currentTokens--;
            return true;
        }
        return false;
    }

    /**
     * 获取当前令牌桶状态信息
     */
    @Override
    public String toString() {
        return String.format("TokenBucket[rate=%dms, capacity=%d, current=%d]",
                rateMs, capacity, currentTokens);
    }
}