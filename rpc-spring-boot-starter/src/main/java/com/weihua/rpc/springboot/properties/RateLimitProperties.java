package com.weihua.rpc.springboot.properties;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 限流器配置属性
 */
@Data
@ConfigurationProperties(prefix = "rpc.ratelimit")
public class RateLimitProperties {

    /**
     * 是否启用限流
     */
    private boolean enabled = true;

    /**
     * 默认QPS限制
     */
    private int defaultQps = 100;

    /**
     * 默认限流策略
     */
    private Strategy defaultStrategy = Strategy.TOKEN_BUCKET;

    /**
     * 是否启用自适应限流
     */
    private boolean adaptiveQps = false;

    /**
     * 每个服务的最大QPS（服务级限流）
     */
    private int maxServiceQps = 5000;

    /**
     * 每个IP的最大QPS（IP级限流）
     */
    private int maxIpQps = 1000;

    /**
     * 令牌桶容量（突发流量处理能力）
     */
    private int burstCapacity = 50;

    /**
     * 令牌填充周期
     * 默认100ms填充一次令牌
     */
    private Duration tokenRefillInterval = Duration.ofMillis(100);

    /**
     * 滑动窗口时间长度
     * 默认1秒的窗口时间
     */
    private Duration slidingWindowSize = Duration.ofSeconds(1);

    /**
     * 限流统计刷新时间
     * 默认5秒刷新一次统计数据
     */
    private Duration statsRefreshInterval = Duration.ofSeconds(5);

    /**
     * 限流熔断恢复时间
     * 默认60秒后自动解除限流
     */
    private Duration limitingRecoveryTime = Duration.ofSeconds(60);

    /**
     * 接口级别限流配置
     */
    private Map<String, InterfaceRateLimit> interfaces = new HashMap<>();

    /**
     * 方法级别限流配置
     */
    private Map<String, MethodRateLimit> methods = new HashMap<>();

    @Data
    public static class InterfaceRateLimit {
        /**
         * 接口QPS限制
         */
        private int qps = 100;

        /**
         * 接口限流策略
         */
        private Strategy strategy = Strategy.TOKEN_BUCKET;
    }

    @Data
    public static class MethodRateLimit {
        /**
         * 方法QPS限制
         */
        private int qps = 50;

        /**
         * 方法限流策略
         */
        private Strategy strategy = Strategy.TOKEN_BUCKET;
    }
}