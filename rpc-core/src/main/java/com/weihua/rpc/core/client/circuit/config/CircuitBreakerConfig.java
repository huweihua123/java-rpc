/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:59:51
 * @LastEditTime: 2025-04-10 01:59:53
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.circuit.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * 熔断器配置
 */
@Slf4j
@Component
public class CircuitBreakerConfig {

    @Autowired
    private Environment environment;

    // 全局默认配置
    @Value("${rpc.circuit.breaker.failures:5}")
    private int failureThreshold;

    @Value("${rpc.circuit.breaker.success.rate.threshold:50}")
    private double successRateThreshold;

    @Value("${rpc.circuit.breaker.reset.timeout.ms:30000}")
    private long resetTimeoutMs;

    @Value("${rpc.circuit.breaker.half.open.requests:10}")
    private int halfOpenMaxRequests;

    // 配置前缀
    private static final String INTERFACE_CONFIG_PREFIX = "rpc.circuit.breaker.interface.";

    @PostConstruct
    public void init() {
        // 将百分比转换为0-1之间的比率
        this.successRateThreshold = this.successRateThreshold / 100.0;

        log.info("熔断器配置初始化: 失败阈值={}, 成功率阈值={}%, 重置超时={}ms, 半开最大请求={}",
                failureThreshold, successRateThreshold * 100, resetTimeoutMs, halfOpenMaxRequests);
    }

    /**
     * 获取失败阈值
     */
    public int getFailureThreshold(String interfaceName) {
        String key = INTERFACE_CONFIG_PREFIX + interfaceName + ".failures";
        return environment.getProperty(key, Integer.class, failureThreshold);
    }

    /**
     * 获取成功率阈值
     */
    public double getSuccessRateThreshold(String interfaceName) {
        String key = INTERFACE_CONFIG_PREFIX + interfaceName + ".success.rate.threshold";
        Double value = environment.getProperty(key, Double.class);
        if (value != null) {
            return value / 100.0; // 将百分比转换为0-1之间的比率
        }
        return successRateThreshold;
    }

    /**
     * 获取重置超时
     */
    public long getResetTimeoutMs(String interfaceName) {
        String key = INTERFACE_CONFIG_PREFIX + interfaceName + ".reset.timeout.ms";
        return environment.getProperty(key, Long.class, resetTimeoutMs);
    }

    /**
     * 获取半开状态最大请求数
     */
    public int getHalfOpenMaxRequests(String interfaceName) {
        String key = INTERFACE_CONFIG_PREFIX + interfaceName + ".half.open.requests";
        return environment.getProperty(key, Integer.class, halfOpenMaxRequests);
    }
}
