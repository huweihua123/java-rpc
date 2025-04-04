/*
 * @Author: weihua hu
 * @Date: 2025-04-04 21:51:38
 * @LastEditTime: 2025-04-05 00:15:58
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.server.rateLimit.config;

import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // 添加导入

/**
 * 限流配置管理器
 */
@Log4j2
public class RateLimitConfigManager {
    private static final RateLimitConfigManager INSTANCE = new RateLimitConfigManager();
    private final ConfigurationManager configManager;

    // 使用ConcurrentHashMap替代HashMap存储接口限流配置
    private final Map<String, InterfaceRateLimit> interfaceRateLimits = new ConcurrentHashMap<>();

    // 默认配置值
    private int defaultRateMs;
    private int defaultCapacity;

    // 配置项键名
    private static final String DEFAULT_RATE_MS_KEY = "rpc.provider.limit.rate";
    private static final String DEFAULT_CAPACITY_KEY = "rpc.provider.limit.capacity";
    private static final String INTERFACE_PREFIX = "rpc.provider.limit.interface.";

    private RateLimitConfigManager() {
        this.configManager = ConfigurationManager.getInstance();
        loadConfig();
    }

    public static RateLimitConfigManager getInstance() {
        return INSTANCE;
    }

    private void loadConfig() {
        // 加载默认配置
        defaultRateMs = configManager.getInt(DEFAULT_RATE_MS_KEY, 10);
        defaultCapacity = configManager.getInt(DEFAULT_CAPACITY_KEY, 100);

        // 清除旧配置，重新加载
        interfaceRateLimits.clear();

        // 加载接口特定配置...
        // (保留原有加载逻辑)
    }

    /**
     * 获取接口限流配置
     */
    public InterfaceRateLimit getInterfaceRateLimit(String interfaceName) {
        // 使用ConcurrentHashMap的computeIfAbsent是线程安全的
        return interfaceRateLimits.computeIfAbsent(interfaceName, key -> {
            // 尝试加载接口特定配置
            String rateKey = INTERFACE_PREFIX + key + ".rate";
            String capacityKey = INTERFACE_PREFIX + key + ".capacity";

            int rateMs = configManager.getInt(rateKey, defaultRateMs);
            int capacity = configManager.getInt(capacityKey, defaultCapacity);

            log.debug("加载接口 {} 的限流配置: 速率={}ms/令牌, 容量={}", key, rateMs, capacity);
            return new InterfaceRateLimit(rateMs, capacity);
        });
    }

    /**
     * 刷新配置
     */
    public void refresh() {
        loadConfig();
        log.info("限流配置已刷新");
    }

    /**
     * 接口限流配置
     */
    @Getter
    public static class InterfaceRateLimit {
        private final int rateMs;
        private final int capacity;

        public InterfaceRateLimit(int rateMs, int capacity) {
            this.rateMs = rateMs;
            this.capacity = capacity;
        }
    }
}