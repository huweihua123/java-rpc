/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:35:53
 * @LastEditTime: 2025-04-22 18:19:50
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import com.weihua.rpc.core.client.invoker.InvokerManager.ConnectionMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC客户端配置属性
 */
@Data
@ConfigurationProperties(prefix = "rpc.client")
public class RpcClientProperties {

    /**
     * 默认请求超时时间
     */
    private Duration timeout = Duration.ofSeconds(3);

    /**
     * 连接超时时间
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 请求处理超时时间
     */
    private Duration requestTimeout = Duration.ofSeconds(10);

    /**
     * 每个地址的初始连接数（目前仅支持0或1）
     */
    private int initConnectionsPerAddress = 1;

    /**
     * 最大重试次数
     */
    private int maxRetries = 2;

    /**
     * 是否启用重试
     */
    private boolean retryEnable = true;

    /**
     * 重试间隔
     */
    private Duration retryInterval = Duration.ofSeconds(1);

    /**
     * 是否只对幂等请求进行重试
     */
    private boolean retryOnlyIdempotent = true;

    /**
     * 指数退避乘数
     * 每次重试后等待时间的增长倍数
     */
    private double backoffMultiplier = 2.0;

    /**
     * 最大退避时间
     */
    private Duration maxBackoffTime = Duration.ofSeconds(30);

    /**
     * 是否添加随机抖动
     * 防止多个客户端同时重试导致的"惊群效应"
     */
    private boolean addJitter = true;

    /**
     * 最小重试间隔
     */
    private Duration minRetryInterval = Duration.ofMillis(500);

    /**
     * 连接模式
     * LAZY: 懒加载，首次使用时创建连接
     * EAGER: 预加载，发现地址后立即创建连接
     */
    private ConnectionMode connectionMode = ConnectionMode.LAZY;

    /**
     * 心跳间隔
     */
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    /**
     * 心跳超时
     */
    private Duration heartbeatTimeout = Duration.ofSeconds(5);

    /**
     * 负载均衡策略：random, roundrobin, leastactive, consistenthash
     */
    private String loadBalance = "random";

    /**
     * 服务发现模式：consul, zk, local
     */
    private String discovery = "consul";

    /**
     * 服务版本
     */
    private String serviceVersion = "1.0.0";

    /**
     * 服务分组
     */
    private String serviceGroup = "default";

    /**
     * 是否启用熔断器
     */
    private boolean circuitBreakerEnable = true;

    /**
     * 接口特定配置
     */
    private Map<String, InterfaceConfig> interfaces = new HashMap<>();

    /**
     * 接口特定配置
     */
    @Data
    public static class InterfaceConfig {
        /**
         * 超时时间
         */
        private Duration timeout;

        /**
         * 最大重试次数
         */
        private Integer retries;

        /**
         * 负载均衡策略
         */
        private String loadBalance;

        /**
         * 是否启用熔断器
         */
        private Boolean circuitBreakerEnable;
    }
}