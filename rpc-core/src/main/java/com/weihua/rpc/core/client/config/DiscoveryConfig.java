/*
 * @Author: weihua hu
 * @Date: 2025-04-23 17:02:45
 * @LastEditTime: 2025-04-24 13:22:50
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * 注册中心配置
 */
@Getter
@Setter
@Slf4j
public class DiscoveryConfig {

    /**
     * 注册中心类型
     */
    private String type = "consul";

    /**
     * 注册中心地址
     */
    private String address = "127.0.0.1:8500";

    /**
     * 连接超时
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 请求超时
     */
    private Duration timeout = Duration.ofSeconds(3);

    /**
     * 重试次数
     */
    private int retryTimes = 3;

    /**
     * 同步周期 (对应YAML中的sync-interval)
     */
    private Duration syncPeriod = Duration.ofSeconds(30);

    /**
     * 是否启用服务健康检查
     */
    private boolean healthCheckEnabled = true;

    /**
     * 健康检查间隔
     */
    private Duration healthCheckInterval = Duration.ofSeconds(12);

    /**
     * 是否缓存服务元数据
     */
    private boolean metadataCache = true;

    /**
     * 元数据缓存过期时间
     */
    private Duration metadataExpireTime = Duration.ofMinutes(5);
    
    /**
     * 故障保护 - 是否启用 (新增)
     */
    private boolean faultToleranceEnabled = true;
    
    /**
     * 故障保护模式 (新增) 
     * 可选值: keep-last-known, fallback-to-local
     */
    private String faultToleranceMode = "keep-last-known";

    @PostConstruct
    public void init() {
        // 初始化逻辑，记录配置信息
        log.info("已加载注册中心配置: 类型={}, 地址={}, 连接超时={}, 请求超时={}, 重试次数={}, 同步周期={}, " +
                "健康检查={}, 元数据缓存={}, 故障保护={}",
                type, address, connectTimeout, timeout, retryTimes, syncPeriod,
                healthCheckEnabled ? "启用(间隔" + healthCheckInterval + ")" : "禁用",
                metadataCache ? "启用(过期时间" + metadataExpireTime + ")" : "禁用",
                faultToleranceEnabled ? "启用(模式:" + faultToleranceMode + ")" : "禁用");
    }
}