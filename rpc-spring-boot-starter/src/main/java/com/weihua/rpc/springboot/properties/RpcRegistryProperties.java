/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:36:14
 * @LastEditTime: 2025-04-15 03:55:28
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

    /**
     * 健康检查间隔（秒）
     * 用于TCP健康检查的检查间隔配置
     */
    private long checkInterval = 10;

    /**
     * 健康检查超时（秒）
     * 每次健康检查的超时时间
     */
    private long checkTimeout = 5;

    /**
     * 服务注销时间
     * 服务被标记为不健康后多久自动注销
     */
    private String deregisterTime = "30s";

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
