/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-23 15:51:29
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit.impl;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
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

    // 填充间隔 - 新增字段
    private final Duration refillInterval;

    public TokenBucketRateLimit(int qps, int burstCapacity) {
        super(qps, Strategy.TOKEN_BUCKET);
        this.burstCapacity = burstCapacity > 0 ? burstCapacity : qps;
        this.tokens = new AtomicLong(this.burstCapacity);
        this.lastRefillTime = System.currentTimeMillis();
        // 默认每秒填充，可以从配置中获取
        this.refillInterval = Duration.ofSeconds(1);
    }

    // 添加自定义填充间隔的构造函数
    public TokenBucketRateLimit(int qps, int burstCapacity, Duration refillInterval) {
        super(qps, Strategy.TOKEN_BUCKET);
        this.burstCapacity = burstCapacity > 0 ? burstCapacity : qps;
        this.tokens = new AtomicLong(this.burstCapacity);
        this.lastRefillTime = System.currentTimeMillis();
        this.refillInterval = refillInterval;
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
        Duration elapsed = Duration.ofMillis(now - lastRefillTime);

        // 计算需要添加的令牌数量
        // 根据经过的时间占refillInterval的比例来填充
        double refillRatio = (double) elapsed.toMillis() / refillInterval.toMillis();
        long tokensToAdd = (long) (refillRatio * qps);

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