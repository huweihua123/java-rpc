package com.weihua.client.circuitBreaker;

import com.weihua.client.circuitBreaker.config.CircuitBreakerConfigManager;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器提供者类
 * 负责创建和管理接口级别的熔断器
 */
@Log4j2
public class CircuitBreakerProvider {
    private static final CircuitBreakerProvider INSTANCE = new CircuitBreakerProvider();
    private final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final CircuitBreakerConfigManager configManager;

    private CircuitBreakerProvider() {
        this.configManager = CircuitBreakerConfigManager.getInstance();
    }

    /**
     * 获取单例实例
     */
    public static CircuitBreakerProvider getInstance() {
        return INSTANCE;
    }

    /**
     * 获取指定接口的熔断器
     * 
     * @param interfaceName 接口名称
     * @return 熔断器实例
     */
    public CircuitBreaker getCircuitBreaker(String interfaceName) {
        return circuitBreakerMap.computeIfAbsent(interfaceName, this::createCircuitBreaker);
    }

    /**
     * 创建接口熔断器
     */
    private CircuitBreaker createCircuitBreaker(String interfaceName) {
        CircuitBreakerConfigManager.CircuitBreakerConfig config = configManager.getInterfaceConfig(interfaceName);

        CircuitBreaker circuitBreaker = new CircuitBreaker(
                config.getFailureThreshold(),
                config.getSuccessRate(),
                config.getWindowMillis(),
                config.getHalfOpenRequests());

        log.info("为接口 {} 创建熔断器: 失败阈值={}, 成功率={}, 窗口期={}ms, 半开状态请求数={}",
                interfaceName,
                config.getFailureThreshold(),
                config.getSuccessRate(),
                config.getWindowMillis(),
                config.getHalfOpenRequests());

        return circuitBreaker;
    }

    /**
     * 刷新熔断器配置
     * 当配置发生变更时调用
     */
    public void refreshConfig() {
        log.info("开始刷新所有熔断器配置...");

        // 为当前已有的熔断器重新创建实例，以应用新配置
        for (String interfaceName : circuitBreakerMap.keySet()) {
            CircuitBreakerConfigManager.CircuitBreakerConfig config = configManager.getInterfaceConfig(interfaceName);

            CircuitBreaker newCircuitBreaker = new CircuitBreaker(
                    config.getFailureThreshold(),
                    config.getSuccessRate(),
                    config.getWindowMillis(),
                    config.getHalfOpenRequests());

            circuitBreakerMap.put(interfaceName, newCircuitBreaker);

            log.debug("已更新接口 {} 的熔断器配置", interfaceName);
        }

        log.info("熔断器配置刷新完成，共更新了 {} 个熔断器", circuitBreakerMap.size());
    }
}