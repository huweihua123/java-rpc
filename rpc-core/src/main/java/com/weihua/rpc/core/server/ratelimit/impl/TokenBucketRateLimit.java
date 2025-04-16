/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-16 14:14:57
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit.impl;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流器实现
 */
@Slf4j
public class TokenBucketRateLimit extends AbstractRateLimiter {
    // 令牌桶当前令牌数
    private final AtomicLong tokens;

    // 上次添加令牌的时间
    private volatile long lastRefillTime;

    // 突发容量（桶容量）
    private final int burstCapacity;

    public TokenBucketRateLimit(int qps, int burstCapacity) {
        super(qps, Strategy.TOKEN_BUCKET);
        this.burstCapacity = burstCapacity > 0 ? burstCapacity : qps;
        this.tokens = new AtomicLong(this.burstCapacity);
        this.lastRefillTime = System.currentTimeMillis();
    }

    public TokenBucketRateLimit(int qps) {
        this(qps, qps);
    }

    @Override
    protected boolean doTryAcquire() {
        refillTokens();

        long currentTokens;
        long newTokens;
        do {
            currentTokens = tokens.get();
            if (currentTokens <= 0) {
                return false;
            }
            newTokens = currentTokens - 1;
        } while (!tokens.compareAndSet(currentTokens, newTokens));

        return true;
    }

    private void refillTokens() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;

        // 计算需要添加的令牌数量
        long tokensToAdd = (long) (elapsed * qps / 1000.0);

        if (tokensToAdd > 0) {
            long currentTokens;
            long newTokens;
            do {
                currentTokens = tokens.get();
                newTokens = Math.min(currentTokens + tokensToAdd, burstCapacity);
            } while (!tokens.compareAndSet(currentTokens, newTokens));

            lastRefillTime = now;
        }
    }

    @Override
    public void updateQps(int newQps) {
        super.updateQps(newQps);
        // 令牌桶大小也需要更新
        long currentTokens;
        long newTokens;
        do {
            currentTokens = tokens.get();
            newTokens = Math.min(currentTokens, newQps);
        } while (!tokens.compareAndSet(currentTokens, newTokens));
    }
}