/*
 * @Author: weihua hu
 * @Date: 2025-04-04 22:08:20
 * @LastEditTime: 2025-04-04 22:09:36
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.circuitBreaker.config;

import common.config.ConfigRefreshManager;
import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

// rpc.consumer.circuit.breaker.failures=5
// rpc.consumer.circuit.breaker.error.rate=50.0
// rpc.consumer.circuit.breaker.window=30000
// rpc.consumer.circuit.breaker.success.threshold=10
/**
 * 熔断器配置管理类
 * 集中管理熔断器相关配置
 */
@Log4j2
public class CircuitBreakerConfigManager implements ConfigRefreshManager.ConfigurableComponent {
    private static final CircuitBreakerConfigManager INSTANCE = new CircuitBreakerConfigManager();
    private final ConfigurationManager configManager;

    // 全局默认配置
    @Getter
    private int defaultFailures;
    @Getter
    private double defaultErrorRate;
    @Getter
    private long defaultWindow;
    @Getter
    private int defaultSuccessThreshold;

    // 配置键常量
    private static final String FAILURES_KEY = "rpc.consumer.circuit.breaker.failures";
    private static final String ERROR_RATE_KEY = "rpc.consumer.circuit.breaker.error.rate";
    private static final String WINDOW_KEY = "rpc.consumer.circuit.breaker.window";
    private static final String SUCCESS_THRESHOLD_KEY = "rpc.consumer.circuit.breaker.success.threshold";

    private CircuitBreakerConfigManager() {
        this.configManager = ConfigurationManager.getInstance();
        loadConfig();
        ConfigRefreshManager.getInstance().register(this);
    }

    public static CircuitBreakerConfigManager getInstance() {
        return INSTANCE;
    }

    private void loadConfig() {
        defaultFailures = configManager.getInt(FAILURES_KEY, 5);
        defaultErrorRate = configManager.getDouble(ERROR_RATE_KEY, 50.0);
        defaultWindow = configManager.getLong(WINDOW_KEY, 30000);
        defaultSuccessThreshold = configManager.getInt(SUCCESS_THRESHOLD_KEY, 10);

        log.debug("加载熔断器配置: 失败阈值={}, 错误率={}%, 窗口期={}ms, 成功阈值={}",
                defaultFailures, defaultErrorRate, defaultWindow, defaultSuccessThreshold);
    }

    /**
     * 获取接口特定的熔断器配置
     * 
     * @param interfaceName 接口名称
     * @return 熔断器配置
     */
    public CircuitBreakerConfig getInterfaceConfig(String interfaceName) {
        String interfacePrefix = "rpc.consumer.interfaces." + interfaceName + ".circuit.breaker.";

        int failures = configManager.getInt(interfacePrefix + "failures", defaultFailures);
        double errorRate = configManager.getDouble(interfacePrefix + "error.rate", defaultErrorRate);
        long window = configManager.getLong(interfacePrefix + "window", defaultWindow);
        int successThreshold = configManager.getInt(interfacePrefix + "success.threshold", defaultSuccessThreshold);

        // 将百分比错误率转换为0-1之间的成功率
        double successRate = (100.0 - errorRate) / 100.0;

        return new CircuitBreakerConfig(failures, successRate, window, successThreshold);
    }

    @Override
    public void refreshConfig() {
        loadConfig();
        log.info("已刷新熔断器配置");
    }

    /**
     * 熔断器配置类
     */
    @Getter
    public static class CircuitBreakerConfig {
        private final int failureThreshold;
        private final double successRate;
        private final long windowMillis;
        private final int halfOpenRequests;

        public CircuitBreakerConfig(int failureThreshold, double successRate, long windowMillis, int halfOpenRequests) {
            this.failureThreshold = failureThreshold;
            this.successRate = successRate;
            this.windowMillis = windowMillis;
            this.halfOpenRequests = halfOpenRequests;
        }
    }
}