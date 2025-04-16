/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-15 15:26:11
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.config;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置
 */
@Getter
@Setter
@Slf4j
public class RateLimitConfig {
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
     * 接口QPS配置映射
     */
    private Map<String, Integer> interfaceQpsConfig = new HashMap<>();

    /**
     * 接口限流策略配置映射
     */
    private Map<String, Strategy> interfaceStrategyConfig = new HashMap<>();

    /**
     * 方法QPS配置映射
     */
    private Map<String, Integer> methodQpsConfig = new HashMap<>();

    /**
     * 方法限流策略配置映射
     */
    private Map<String, Strategy> methodStrategyConfig = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("限流配置初始化完成: 启用状态={}, 默认QPS={}, 默认策略={}, 自适应限流={}",
                enabled ? "启用" : "禁用", defaultQps, defaultStrategy,
                adaptiveQps ? "启用" : "禁用");
    }
}