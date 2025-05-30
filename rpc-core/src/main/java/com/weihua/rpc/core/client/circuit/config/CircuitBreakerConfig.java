/*
 * @Author: weihua hu
 * @Date: 2025-04-15 02:06:43
 * @LastEditTime: 2025-04-23 15:43:19
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.circuit.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * 熔断器配置
 */
@Slf4j
public class CircuitBreakerConfig {

    private final Environment environment;

    // 全局默认配置
    private int failureThreshold;
    private double successRateThreshold;
    private Duration resetTimeout;
    private int halfOpenMaxRequests;

    // 配置前缀
    private static final String INTERFACE_CONFIG_PREFIX = "rpc.circuit.breaker.interface.";

    /**
     * 构造函数
     * 
     * @param environment          Spring环境，用于获取特定接口配置
     * @param failureThreshold     失败阈值
     * @param successRateThreshold 成功率阈值（百分比，0-100）
     * @param resetTimeout         重置超时
     * @param halfOpenMaxRequests  半开状态最大请求数
     */
    public CircuitBreakerConfig(Environment environment,
            int failureThreshold,
            double successRateThreshold,
            Duration resetTimeout,
            int halfOpenMaxRequests) {
        this.environment = environment;
        this.failureThreshold = failureThreshold;
        this.successRateThreshold = successRateThreshold / 100.0; // 转换为0-1之间的比率
        this.resetTimeout = resetTimeout;
        this.halfOpenMaxRequests = halfOpenMaxRequests;

        log.info("熔断器配置初始化: 失败阈值={}, 成功率阈值={}%, 重置超时={}, 半开最大请求={}",
                failureThreshold, this.successRateThreshold * 100, resetTimeout, halfOpenMaxRequests);
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
    public Duration getResetTimeout(String interfaceName) {
        String key = INTERFACE_CONFIG_PREFIX + interfaceName + ".reset.timeout";
        return environment.getProperty(key, Duration.class, resetTimeout);
    }

    /**
     * 获取重置超时（毫秒）
     * @deprecated 使用 {@link #getResetTimeout(String)} 替代
     */
    @Deprecated
    public long getResetTimeoutMs(String interfaceName) {
        return getResetTimeout(interfaceName).toMillis();
    }

    /**
     * 获取半开状态最大请求数
     */
    public int getHalfOpenMaxRequests(String interfaceName) {
        String key = INTERFACE_CONFIG_PREFIX + interfaceName + ".half.open.requests";
        return environment.getProperty(key, Integer.class, halfOpenMaxRequests);
    }
}