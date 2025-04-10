/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:35:53
 * @LastEditTime: 2025-04-10 02:35:55
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RPC客户端配置属性
 */
@Data
@ConfigurationProperties(prefix = "rpc.client")
public class RpcClientProperties {

    /**
     * 默认请求超时时间（毫秒）
     */
    private int timeout = 3000;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 重试次数
     */
    private int retries = 2;

    /**
     * 是否启用重试
     */
    private boolean retryEnable = true;

    /**
     * 重试间隔（毫秒）
     */
    private int retryInterval = 1000;

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
     * 熔断器相关配置
     */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * 熔断器配置
     */
    @Data
    public static class CircuitBreaker {
        /**
         * 连续失败阈值
         */
        private int failureThreshold = 5;

        /**
         * 错误率阈值（百分比）
         */
        private int errorRateThreshold = 50;

        /**
         * 重置超时（毫秒）
         */
        private long resetTimeout = 30000;

        /**
         * 半开状态最大请求数
         */
        private int halfOpenMaxRequests = 10;
    }
}
