/*
 * @Author: weihua hu
 * @Date: 2025-04-12 18:30:11
 * @LastEditTime: 2025-04-12 20:26:39
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流器工厂，负责创建不同类型的限流器
 */
@Slf4j
public class RateLimitFactory {

    /**
     * 创建限流器
     * 
     * @param name     限流器名称（接口或方法签名）
     * @param maxQps   最大QPS
     * @param strategy 限流策略
     * @return 限流器实例
     */
    public static RateLimit createRateLimit(String name, int maxQps, Strategy strategy) {
        switch (strategy) {
            case TOKEN_BUCKET:
                return new TokenBucketRateLimit(name, maxQps);
            case LEAKY_BUCKET:
                return new LeakyBucketRateLimit(name, maxQps);
            case SLIDING_WINDOW:
                return new SlidingWindowRateLimit(name, maxQps);
            case COUNTER:
                return new CounterRateLimit(name, maxQps);
            default:
                log.warn("不支持的限流策略: {}, 使用默认令牌桶策略", strategy);
                return new TokenBucketRateLimit(name, maxQps);
        }
    }
}