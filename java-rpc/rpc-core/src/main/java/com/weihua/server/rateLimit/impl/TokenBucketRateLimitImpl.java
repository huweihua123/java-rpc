package com.weihua.server.rateLimit.impl;

import server.rateLimit.RateLimit;

public class TokenBucketRateLimitImpl implements RateLimit {
    // 令牌产生间隔（毫秒）
    private final int rateMs;
    // 桶的容量
    private final int capacity;
    // 当前令牌数量
    private volatile int currentTokens;
    // 上次令牌发放时间
    private volatile long lastRefillTime;

    public TokenBucketRateLimitImpl(int rateMs, int capacity) {
        this.rateMs = rateMs;
        this.capacity = capacity;
        this.currentTokens = capacity;
        this.lastRefillTime = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean getToken() {
        long now = System.currentTimeMillis();
        // 计算需要补充的令牌数
        int newTokens = (int) ((now - lastRefillTime) / rateMs);
        if (newTokens > 0) {
            currentTokens = Math.min(capacity, currentTokens + newTokens);
            lastRefillTime = now;
        }

        if (currentTokens > 0) {
            currentTokens--;
            return true;
        }
        return false;
    }
}