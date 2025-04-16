/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-15 17:05:00
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流器工厂类，作为SPI机制的补充，提供默认实现的创建
 */
@Slf4j
public class RateLimitFactory {

    /**
     * 创建限流器实例
     * 
     * @param strategy      限流策略
     * @param qps           QPS限制
     * @param burstCapacity 突发容量（令牌桶容量）
     * @return 限流器实例
     */
    public static RateLimit create(Strategy strategy, int qps, int burstCapacity) {
        log.debug("通过工厂创建限流器: 策略={}, QPS={}, 突发容量={}", strategy, qps, burstCapacity);

        switch (strategy) {
            case TOKEN_BUCKET:
                return new TokenBucketRateLimit(qps, burstCapacity);
            case LEAKY_BUCKET:
                // 在这里实现漏桶限流器，也需要继承AbstractRateLimiter
                // 如果没有对应实现，可以暂时返回默认实现
                log.warn("暂未实现漏桶限流器，使用令牌桶替代");
                return new TokenBucketRateLimit(qps, burstCapacity);
            case SLIDING_WINDOW:
                // 在这里实现滑动窗口限流器，也需要继承AbstractRateLimiter
                // 如果没有对应实现，可以暂时返回默认实现
                log.warn("暂未实现滑动窗口限流器，使用令牌桶替代");
                return new TokenBucketRateLimit(qps, burstCapacity);
            default:
                log.warn("未知的限流策略: {}, 使用默认令牌桶策略", strategy);
                return new TokenBucketRateLimit(qps, burstCapacity);
        }
    }

    /**
     * 创建限流器实例，使用默认突发容量
     * 
     * @param strategy 限流策略
     * @param qps      QPS限制
     * @return 限流器实例
     */
    public static RateLimit create(Strategy strategy, int qps) {
        return create(strategy, qps, qps);
    }
}