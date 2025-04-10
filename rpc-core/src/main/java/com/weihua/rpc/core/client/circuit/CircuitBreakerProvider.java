/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:59:33
 * @LastEditTime: 2025-04-10 01:59:34
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.circuit;

import com.weihua.rpc.core.client.circuit.config.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 熔断器提供者
 * 管理和提供各服务的熔断器实例
 */
@Component
public class CircuitBreakerProvider {

    @Autowired
    private CircuitBreakerConfig circuitBreakerConfig;

    // 存储各接口的熔断器
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    // 状态打印调度器
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // 创建并启动状态打印调度器
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "circuit-breaker-monitor");
            t.setDaemon(true);
            return t;
        });

        // 每60秒打印一次熔断器状态
        scheduler.scheduleAtFixedRate(this::printCircuitBreakerStatus,
                60, 60, TimeUnit.SECONDS);
    }

    /**
     * 获取指定接口的熔断器
     */
    public CircuitBreaker getCircuitBreaker(String interfaceName) {
        return breakers.computeIfAbsent(interfaceName, this::createCircuitBreaker);
    }

    /**
     * 创建熔断器
     */
    protected CircuitBreaker createCircuitBreaker(String interfaceName) {
        // 获取接口特定的熔断器配置
        int failureThreshold = circuitBreakerConfig.getFailureThreshold(interfaceName);
        double failureRateThreshold = 1.0 - circuitBreakerConfig.getSuccessRateThreshold(interfaceName); // 转换成失败率
        long resetTimeoutMs = circuitBreakerConfig.getResetTimeoutMs(interfaceName);
        int halfOpenMaxRequests = circuitBreakerConfig.getHalfOpenMaxRequests(interfaceName);

        return new DefaultCircuitBreaker(failureThreshold, failureRateThreshold,
                resetTimeoutMs, halfOpenMaxRequests);
    }

    /**
     * 打印熔断器状态
     */
    private void printCircuitBreakerStatus() {
        breakers.forEach((interfaceName, breaker) -> {
            if (breaker instanceof DefaultCircuitBreaker) {
                DefaultCircuitBreaker defaultBreaker = (DefaultCircuitBreaker) breaker;
                String status = String.format("接口: %s, 状态: %s, 错误率: %.2f%%",
                        interfaceName, breaker.getState(),
                        defaultBreaker.getErrorRate() * 100);
                System.out.println("[熔断器状态] " + status);
            }
        });
    }

    /**
     * 清理资源
     */
    @PreDestroy
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        breakers.clear();
    }
}
