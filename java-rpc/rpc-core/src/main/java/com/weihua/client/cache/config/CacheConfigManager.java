/*
 * @Author: weihua hu
 * @Date: 2025-04-04 22:37:00
 * @LastEditTime: 2025-04-04 22:37:01
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.cache.config;

import common.config.ConfigRefreshManager;
import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * 缓存配置管理类
 * 集中管理缓存相关配置
 */
@Log4j2
public class CacheConfigManager implements ConfigRefreshManager.ConfigurableComponent {
    private static final CacheConfigManager INSTANCE = new CacheConfigManager();
    private final ConfigurationManager configManager;

    // 缓存配置项
    @Getter
    private long serviceExpireTime; // 服务缓存过期时间(毫秒)
    @Getter
    private int serviceMaxSize; // 服务缓存最大大小
    @Getter
    private int serviceCleanupInterval; // 服务缓存清理间隔(秒)

    // 结果缓存配置
    @Getter
    private boolean resultCacheEnable; // 是否启用结果缓存
    @Getter
    private long resultCacheExpireTime; // 结果缓存过期时间(毫秒)
    @Getter
    private int resultCacheMaxSize; // 结果缓存最大大小

    private CacheConfigManager() {
        this.configManager = ConfigurationManager.getInstance();
        loadConfig();
        // 注册到配置刷新管理器
        ConfigRefreshManager.getInstance().register(this);
    }

    public static CacheConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * 加载配置项
     */
    private void loadConfig() {
        // 服务缓存配置
        serviceExpireTime = configManager.getLong("rpc.cache.service.expire", 600000);
        serviceMaxSize = configManager.getInt("rpc.cache.service.max.size", 10000);
        serviceCleanupInterval = configManager.getInt("rpc.cache.service.cleanup.interval", 300);

        // 结果缓存配置
        resultCacheEnable = configManager.getBoolean("rpc.cache.result.enable", false);
        resultCacheExpireTime = configManager.getLong("rpc.cache.result.expire", 60000);
        resultCacheMaxSize = configManager.getInt("rpc.cache.result.max.size", 5000);

        log.info("缓存配置已加载: 服务缓存过期时间={}ms, 清理间隔={}s, 最大大小={}",
                serviceExpireTime, serviceCleanupInterval, serviceMaxSize);
    }

    /**
     * 刷新配置
     */
    @Override
    public void refreshConfig() {
        loadConfig();
        log.info("缓存配置已刷新");
    }
}