/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:36:14
 * @LastEditTime: 2025-04-23 16:06:38
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RPC注册中心配置属性
 */
@Data
@ConfigurationProperties(prefix = "rpc.registry")
public class RpcRegistryProperties {

    /**
     * 注册中心类型：consul, zookeeper, local
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
     * 服务同步周期
     */
    private Duration syncPeriod = Duration.ofSeconds(30);

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

    /**
     * 是否启用健康检查
     */
    private boolean healthCheckEnabled = true;

    /**
     * 健康检查路径（HTTP检查模式下使用）
     */
    private String healthCheckPath = "/health";

    /**
     * 注册标签列表，用逗号分隔
     */
    private String tags = "";

    /**
     * 是否启用元数据
     */
    private boolean metadataEnabled = true;
}