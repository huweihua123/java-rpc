/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:36:14
 * @LastEditTime: 2025-04-10 02:36:15
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * 连接超时（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 请求超时（毫秒）
     */
    private int timeout = 5000;

    /**
     * 重试次数
     */
    private int retryTimes = 3;

    /**
     * 服务健康检查间隔（秒）
     */
    private int healthCheckPeriod = 10;

    /**
     * 服务同步周期（秒）
     */
    private int syncPeriod = 30;
}
