/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:20:37
 * @LastEditTime: 2025-04-23 15:46:30
 * @LastEditors: weihua hu
 * @Description: 注册中心配置
 */
package com.weihua.rpc.core.server.config;

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
public class RegistryConfig {

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
    private Duration timeout = Duration.ofSeconds(5);

    /**
     * 重试次数
     */
    private int retryTimes = 3;

    /**
     * 服务健康检查间隔
     */
    private Duration healthCheckPeriod = Duration.ofSeconds(10);

    /**
     * 健康检查间隔
     * 用于TCP健康检查的检查间隔配置
     */
    private Duration checkInterval = Duration.ofSeconds(10);

    /**
     * 健康检查超时
     * 每次健康检查的超时时间
     */
    private Duration checkTimeout = Duration.ofSeconds(5);

    /**
     * 服务注销时间
     * 服务被标记为不健康后多久自动注销
     */
    private Duration deregisterTime = Duration.ofSeconds(30);

    @PostConstruct
    public void init() {
        log.info("注册中心配置: 类型={}, 地址={}, 连接超时={}ms, 请求超时={}ms, 重试次数={}, 健康检查间隔={}s, TCP检查间隔={}s, TCP检查超时={}s, 服务注销时间={}s",
                type, address, connectTimeout.toMillis(), timeout.toMillis(), retryTimes, 
                healthCheckPeriod.getSeconds(), checkInterval.getSeconds(), 
                checkTimeout.getSeconds(), deregisterTime.getSeconds());
    }
}