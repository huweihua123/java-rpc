/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-15 17:00:00
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.common.extension.ExtensionLoader;
import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import com.weihua.rpc.core.server.config.RateLimitConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流器提供者，负责创建、缓存和获取限流器实例
 */
@Slf4j
public class RateLimitProvider {

    private final RateLimitConfig config;

    /**
     * 方法级限流器缓存
     * key: 方法全限定名 (接口名.方法名)
     * value: 限流器实例
     */
    private final Map<String, RateLimit> methodLimiterCache = new ConcurrentHashMap<>();

    /**
     * 接口级限流器缓存
     * key: 接口全限定名
     * value: 限流器实例
     */
    private final Map<String, RateLimit> interfaceLimiterCache = new ConcurrentHashMap<>();

    /**
     * 服务级限流器
     */
    private volatile RateLimit serviceLimiter;

    /**
     * IP级限流器缓存
     * key: IP地址
     * value: 限流器实例
     */
    private final Map<String, RateLimit> ipLimiterCache = new ConcurrentHashMap<>();

    public RateLimitProvider(RateLimitConfig config) {
        this.config = config;
    }

    /**
     * 获取方法级限流器
     */
    public RateLimit getMethodLimiter(String methodKey) {
        return methodLimiterCache.computeIfAbsent(methodKey, key -> {
            int qps = config.getMethodQpsConfig().getOrDefault(key, config.getDefaultQps());
            Strategy strategy = config.getMethodStrategyConfig().getOrDefault(key, config.getDefaultStrategy());
            return createRateLimiter(strategy, qps);
        });
    }

    /**
     * 获取接口级限流器
     */
    public RateLimit getInterfaceLimiter(String interfaceKey) {
        return interfaceLimiterCache.computeIfAbsent(interfaceKey, key -> {
            int qps = config.getInterfaceQpsConfig().getOrDefault(key, config.getDefaultQps());
            Strategy strategy = config.getInterfaceStrategyConfig().getOrDefault(key, config.getDefaultStrategy());
            return createRateLimiter(strategy, qps);
        });
    }

    /**
     * 获取服务级限流器
     */
    public RateLimit getServiceLimiter() {
        if (serviceLimiter == null) {
            synchronized (this) {
                if (serviceLimiter == null) {
                    serviceLimiter = createRateLimiter(config.getDefaultStrategy(), config.getMaxServiceQps());
                }
            }
        }
        return serviceLimiter;
    }

    /**
     * 获取IP级限流器
     */
    public RateLimit getIpLimiter(String ip) {
        return ipLimiterCache.computeIfAbsent(ip,
                key -> createRateLimiter(config.getDefaultStrategy(), config.getMaxIpQps()));
    }

    /**
     * 创建限流器实例，使用SPI机制
     */
    private RateLimit createRateLimiter(Strategy strategy, int qps) {
        String extensionName = strategy.name().toLowerCase();

        try {
            // 通过SPI机制加载对应的限流器实现
            RateLimit limiter = ExtensionLoader.getExtensionLoader(RateLimit.class)
                    .getExtension(extensionName);

            // 如果是新创建的实例，需要设置QPS
            if (limiter.getQps() != qps) {
                limiter.updateQps(qps);
            }

            return limiter;
        } catch (Exception e) {
            // SPI加载失败，回退到工厂模式创建
            log.warn("通过SPI加载限流器失败: {}, 回退到默认工厂模式", e.getMessage());
            return RateLimitFactory.create(strategy, qps, config.getBurstCapacity());
        }
    }

    /**
     * 清除所有缓存的限流器
     */
    public void clearAll() {
        methodLimiterCache.clear();
        interfaceLimiterCache.clear();
        ipLimiterCache.clear();
        serviceLimiter = null;
        log.info("已清除所有限流器缓存");
    }
}