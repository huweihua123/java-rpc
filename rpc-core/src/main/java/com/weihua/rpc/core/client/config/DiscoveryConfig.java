/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:01:21
 * @LastEditTime: 2025-04-11 19:59:50
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 注册中心配置
 */
@Component
@Getter
@Setter
public class DiscoveryConfig {

    /**
     * 注册中心类型
     */
    @Value("${rpc.discovery:consul}")
    private String type;

    /**
     * 注册中心地址
     */
    @Value("${rpc.discovery.address:127.0.0.1:8500}")
    private String address;

    /**
     * 连接超时（毫秒）
     */
    @Value("${rpc.discovery.connect.timeout:5000}")
    private int connectTimeout;

    /**
     * 请求超时（毫秒）
     */
    @Value("${rpc.discovery.timeout:15000}")
    private int timeout;

    /**
     * 重试次数
     */
    @Value("${rpc.discovery.retry.times:3}")
    private int retryTimes;

    /**
     * 同步周期（秒）
     */
    @Value("${rpc.discovery.sync.period:30}")
    private int syncPeriod;

    /**
     * 是否启用服务健康检查
     */
    @Value("${rpc.discovery.healthcheck.enabled:true}")
    private boolean healthCheckEnabled;

    /**
     * 健康检查间隔（秒）
     */
    @Value("${rpc.discovery.healthcheck.interval:15}")
    private int healthCheckInterval;

    /**
     * 是否缓存服务元数据
     */
    @Value("${rpc.discovery.metadata.cache:true}")
    private boolean metadataCache;

    /**
     * 元数据缓存过期时间（秒）
     */
    @Value("${rpc.discovery.metadata.expire:300}")
    private int metadataExpireSeconds;

    @PostConstruct
    public void init() {
        // 初始化逻辑，如需记录配置信息可在此添加
    }
}
