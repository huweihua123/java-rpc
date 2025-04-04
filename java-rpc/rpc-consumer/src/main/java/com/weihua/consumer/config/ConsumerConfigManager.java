/*
 * @Author: weihua hu
 * @Date: 2025-04-05 19:00:00
 * @LastEditTime: 2025-04-04 21:53:20
 * @LastEditors: weihua hu
 * @Description: 消费者配置管理类
 */
package com.weihua.consumer.config;

import common.config.ConfigRefreshManager;
import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * 消费者配置管理类
 * 集中管理和访问消费者配置，替代硬编码配置
 */
@Log4j2
public class ConsumerConfigManager implements ConfigRefreshManager.ConfigurableComponent {
    private static final ConsumerConfigManager INSTANCE = new ConsumerConfigManager();
    private final ConfigurationManager configManager;

    // 常用配置项的缓存
    @Getter
    private String applicationName;
    @Getter
    private boolean checkAvailable;
    @Getter
    private boolean subscribeOnStart;
    @Getter
    private int discoveryRetryTimes;
    @Getter
    private String failoverPolicy;
    @Getter
    private int timeout;
    @Getter
    private int interfacesTimeout;
    @Getter
    private String loadBalanceStrategy;
    @Getter
    private String downgradeStrategy;
    @Getter
    private boolean genericEnable;
    @Getter
    private int poolMaxIdle;
    @Getter
    private int poolMinIdle;
    @Getter
    private int connectionIdleTimeout;
    @Getter
    private int connectionRetry;
    @Getter
    private boolean connectionPoolEnable;
    @Getter
    private int circuitBreakerFailures;
    @Getter
    private double circuitBreakerErrorRate;
    @Getter
    private int circuitBreakerWindow;
    @Getter
    private int circuitBreakerSuccessThreshold;
    @Getter
    private boolean retryEnable;
    @Getter
    private int retryMaxTimes;
    @Getter
    private int retryInterval;
    @Getter
    private boolean retryNewConnection;

    private ConsumerConfigManager() {
        this.configManager = ConfigurationManager.getInstance();
        loadConfig();
        // 注册到配置刷新管理器
        ConfigRefreshManager.getInstance().register(this);
    }

    public static ConsumerConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * 加载配置项
     */
    private void loadConfig() {
        // 应用信息
        applicationName = configManager.getString("rpc.application.name", "java-rpc-consumer");

        // 消费者行为配置
        checkAvailable = configManager.getBoolean("rpc.consumer.check", false);
        subscribeOnStart = configManager.getBoolean("rpc.consumer.subscribe", true);
        discoveryRetryTimes = configManager.getInt("rpc.consumer.discovery.retry.times", 3);
        failoverPolicy = configManager.getString("rpc.consumer.cluster.failover.policy", "retry");
        timeout = configManager.getInt("rpc.consumer.timeout", 3000);
        interfacesTimeout = configManager.getInt("rpc.consumer.interfaces.timeout", 5000);
        loadBalanceStrategy = configManager.getString("rpc.consumer.loadbalance", "consistentHash");
        downgradeStrategy = configManager.getString("rpc.consumer.downgrade.strategy", "return_null");

        // 泛化调用
        genericEnable = configManager.getBoolean("rpc.consumer.generic.enable", false);

        // 连接管理
        poolMaxIdle = configManager.getInt("rpc.consumer.pool.max.idle", 16);
        poolMinIdle = configManager.getInt("rpc.consumer.pool.min.idle", 4);
        connectionIdleTimeout = configManager.getInt("rpc.consumer.connection.idle.timeout", 60000);
        connectionRetry = configManager.getInt("rpc.consumer.connection.retry", 3);
        connectionPoolEnable = configManager.getBoolean("rpc.consumer.connection.pool.enable", true);

        // 熔断器配置
        circuitBreakerFailures = configManager.getInt("rpc.consumer.circuit.breaker.failures", 5);
        circuitBreakerErrorRate = configManager.getDouble("rpc.consumer.circuit.breaker.error.rate", 50.0);
        circuitBreakerWindow = configManager.getInt("rpc.consumer.circuit.breaker.window", 10000);
        circuitBreakerSuccessThreshold = configManager.getInt("rpc.consumer.circuit.breaker.success.threshold", 3);

        // 重试策略
        retryEnable = configManager.getBoolean("rpc.consumer.retry.enable", true);
        retryMaxTimes = configManager.getInt("rpc.consumer.retry.max.times", 3);
        retryInterval = configManager.getInt("rpc.consumer.retry.interval", 1000);
        retryNewConnection = configManager.getBoolean("rpc.consumer.retry.new.connection", false);
    }

    /**
     * 刷新配置
     */
    public void refresh() {
        loadConfig();
        log.info("已刷新消费者配置");
        printConfig();
    }

    /**
     * 打印当前配置
     */
    public void printConfig() {
        log.info("===== RPC Consumer Configuration =====");
        log.info("应用名称: {}", applicationName);
        log.info("检查提供者可用性: {}", checkAvailable);
        log.info("启动时订阅: {}", subscribeOnStart);
        log.info("服务发现重试次数: {}", discoveryRetryTimes);
        log.info("集群故障策略: {}", failoverPolicy);
        log.info("请求超时(ms): {}", timeout);
        log.info("负载均衡策略: {}", loadBalanceStrategy);
        log.info("降级策略: {}", downgradeStrategy);
        log.info("连接池启用: {}", connectionPoolEnable);
        log.info("最大空闲连接数: {}", poolMaxIdle);
        log.info("熔断器失败阈值: {}", circuitBreakerFailures);
        log.info("熔断器错误率: {}%", circuitBreakerErrorRate);
        log.info("重试启用: {}", retryEnable);
        log.info("最大重试次数: {}", retryMaxTimes);
        log.info("===================================");
    }

    /**
     * 获取特定接口的超时时间
     * 
     * @param interfaceName 接口全限定名
     * @return 超时时间(毫秒)
     */
    public int getInterfaceTimeout(String interfaceName) {
        String key = "rpc.consumer.interface." + interfaceName + ".timeout";
        return configManager.getInt(key, timeout);
    }

    /**
     * 刷新配置
     */
    @Override
    public void refreshConfig() {
        loadConfig();
        log.info("已刷新消费者配置");
        printConfig();
    }
}
