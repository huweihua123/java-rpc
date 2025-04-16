/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:01:21
 * @LastEditTime: 2025-04-14 16:25:32
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;

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
     * 连接超时（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 请求超时（毫秒）
     */
    private int timeout = 15000;

    /**
     * 重试次数
     */
    private int retryTimes = 3;

    /**
     * 同步周期（秒）
     */
    private int syncPeriod = 30;

    /**
     * 是否启用服务健康检查
     */
    private boolean healthCheckEnabled = true;

    /**
     * 健康检查间隔（秒）
     */
    private int healthCheckInterval = 15;

    /**
     * 是否缓存服务元数据
     */
    private boolean metadataCache = true;

    /**
     * 元数据缓存过期时间（秒）
     */
    private int metadataExpireSeconds = 300;

    @PostConstruct
    public void init() {
        // 初始化逻辑，记录配置信息
        log.info("已加载注册中心配置: 类型={}, 地址={}, 连接超时={}ms, 请求超时={}ms, 重试次数={}, 同步周期={}秒, " +
                "健康检查={}, 元数据缓存={}",
                type, address, connectTimeout, timeout, retryTimes, syncPeriod,
                healthCheckEnabled ? "启用(间隔" + healthCheckInterval + "秒)" : "禁用",
                metadataCache ? "启用(过期时间" + metadataExpireSeconds + "秒)" : "禁用");
    }
}