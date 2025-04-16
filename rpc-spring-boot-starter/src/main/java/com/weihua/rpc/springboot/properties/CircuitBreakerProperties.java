/*
 * @Author: weihua hu
 * @Date: 2025-04-15 02:03:43
 * @LastEditTime: 2025-04-15 02:03:46
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断器配置属性
 */
@Data
@ConfigurationProperties(prefix = "rpc.circuit.breaker")
public class CircuitBreakerProperties {

    /**
     * 失败阈值，达到此阈值将触发熔断
     */
    private int failures = 5;

    /**
     * 成功率阈值，低于此阈值将触发熔断（百分比值：0-100）
     */
    private double successRateThreshold = 50.0;

    /**
     * 熔断器重置超时时间（毫秒）
     */
    private long resetTimeoutMs = 30000;

    /**
     * 半开状态最大请求数
     */
    private int halfOpenRequests = 10;
    
    /**
     * 接口级别配置，优先级高于全局配置
     */
    private Map<String, InterfaceCircuitBreakerConfig> interfaces = new HashMap<>();
    
    /**
     * 接口级别熔断器配置
     */
    @Data
    public static class InterfaceCircuitBreakerConfig {
        private int failures;
        private double successRateThreshold;
        private long resetTimeoutMs;
        private int halfOpenRequests;
    }
}