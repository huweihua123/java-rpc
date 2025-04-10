/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:20:37
 * @LastEditTime: 2025-04-10 19:34:19
 * @LastEditors: weihua hu
 * @Description: 注册中心配置
 */
package com.weihua.rpc.core.server.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 注册中心配置
 */
@Component
@Getter
@Setter
@Slf4j
public class RegistryConfig {

    /**
     * 注册中心类型
     */
    @Value("${rpc.registry.type:consul}")
    private String type;

    /**
     * 注册中心地址
     */
    @Value("${rpc.registry.address:127.0.0.1:8500}")
    private String address;

    /**
     * 连接超时（毫秒）
     */
    @Value("${rpc.registry.connect.timeout:5000}")
    private int connectTimeout;

    /**
     * 请求超时（毫秒）
     */
    @Value("${rpc.registry.timeout:5000}")
    private int timeout;

    /**
     * 重试次数
     */
    @Value("${rpc.registry.retry.times:3}")
    private int retryTimes;

    /**
     * 服务健康检查间隔（秒）
     */
    @Value("${rpc.registry.health.check.period:10}")
    private int healthCheckPeriod;

    /**
     * 健康检查间隔（例如："10s"）
     * 用于TCP健康检查的检查间隔配置
     */
    @Value("${rpc.registry.check.interval:10}")
    private long checkInterval;

    /**
     * 健康检查超时（例如："5s"）
     * 每次健康检查的超时时间
     */
    @Value("${rpc.registry.check.timeout:5}")
    private long checkTimeout;

    /**
     * 服务注销时间（例如："30s"）
     * 服务被标记为不健康后多久自动注销
     */
    @Value("${rpc.registry.deregister.time:30s}")
    private String deregisterTime;

    @PostConstruct
    public void init() {
        log.info("注册中心配置: 类型={}, 地址={}, 连接超时={}ms, 请求超时={}ms, 重试次数={}, 健康检查间隔={}s, TCP检查间隔={}, TCP检查超时={}, 服务注销时间={}",
                type, address, connectTimeout, timeout, retryTimes, healthCheckPeriod,
                checkInterval, checkTimeout, deregisterTime);
    }
}