package com.weihua.client.config;

import common.config.ConfigRefreshManager;
import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * 注册中心配置管理类
 * 集中管理注册中心相关配置
 */
@Log4j2
public class RegistryConfigManager implements ConfigRefreshManager.ConfigurableComponent {
    // 单例实例
    private static final RegistryConfigManager INSTANCE = new RegistryConfigManager();
    private final ConfigurationManager configManager;

    // 通用配置
    @Getter
    private String registryType;
    @Getter
    private boolean registerEnable;
    @Getter
    private boolean subscribeEnable;
    @Getter
    private String loadBalanceStrategy;

    // Consul配置项
    @Getter
    private String consulHost;
    @Getter
    private int consulPort;
    @Getter
    private int consulTimeout;
    @Getter
    private int consulConnectTimeout;
    @Getter
    private int consulWriteTimeout;
    @Getter
    private String consulCheckInterval;
    @Getter
    private String consulCheckTimeout;
    @Getter
    private String consulDeregisterTime;
    @Getter
    private int consulSyncPeriod;

    // ZooKeeper配置项
    @Getter
    private String zkAddress;
    @Getter
    private int zkSessionTimeout;
    @Getter
    private int zkConnectionTimeout;
    @Getter
    private int zkBaseSleepTime;
    @Getter
    private int zkMaxRetries;
    @Getter
    private String zkRootPath;

    private RegistryConfigManager() {
        this.configManager = ConfigurationManager.getInstance();
        loadConfig();
        // 注册到配置刷新管理器
        ConfigRefreshManager.getInstance().register(this);
    }

    public static RegistryConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * 加载配置项
     */
    private void loadConfig() {
        // 通用配置
        registryType = configManager.getString("rpc.registry", "consul");
        registerEnable = configManager.getBoolean("rpc.registry.register", true);
        subscribeEnable = configManager.getBoolean("rpc.registry.subscribe", true);
        loadBalanceStrategy = configManager.getString("rpc.loadbalance", "random");

        // Consul配置
        consulHost = configManager.getString("rpc.registry.consul.host", "localhost");
        consulPort = configManager.getInt("rpc.registry.consul.port", 8500);
        consulTimeout = configManager.getInt("rpc.registry.consul.timeout", 10000);
        consulConnectTimeout = configManager.getInt("rpc.registry.consul.connect.timeout", consulTimeout / 2);
        consulWriteTimeout = configManager.getInt("rpc.registry.consul.write.timeout", consulTimeout);
        consulCheckInterval = ensureTimeUnit(configManager.getString("rpc.registry.consul.check.interval", "10"), "s");
        consulCheckTimeout = ensureTimeUnit(configManager.getString("rpc.registry.consul.check.timeout", "5"), "s");
        consulDeregisterTime = ensureTimeUnit(configManager.getString("rpc.registry.consul.deregister.time", "30"),
                "s");
        consulSyncPeriod = configManager.getInt("rpc.registry.consul.sync.period", 30);

        // ZooKeeper配置
        zkAddress = configManager.getString("rpc.registry.zookeeper.address", "127.0.0.1:2181");
        zkSessionTimeout = configManager.getInt("rpc.registry.zookeeper.session.timeout", 40000);
        zkConnectionTimeout = configManager.getInt("rpc.registry.zookeeper.connection.timeout", 10000);
        zkBaseSleepTime = configManager.getInt("rpc.registry.zookeeper.retry.base.sleep", 1000);
        zkMaxRetries = configManager.getInt("rpc.registry.zookeeper.retry.max.times", 3);
        zkRootPath = configManager.getString("rpc.registry.zookeeper.root.path", "MyRPC");

        log.info("已加载注册中心配置，类型: {}, 负载均衡策略: {}", registryType, loadBalanceStrategy);
    }

    /**
     * 刷新配置（实现ConfigurableComponent接口）
     */
    @Override
    public void refreshConfig() {
        loadConfig();
        log.info("已刷新注册中心配置");
    }

    /**
     * 确保时间值包含单位
     */
    private String ensureTimeUnit(String value, String defaultUnit) {
        if (value == null || value.isEmpty()) {
            return "10" + defaultUnit;
        }

        if (value.endsWith("s") || value.endsWith("m") || value.endsWith("h")) {
            return value;
        }

        String numericPart = value.trim().replaceAll("[^0-9]", "");
        if (numericPart.isEmpty()) {
            return "10" + defaultUnit;
        }

        return numericPart + defaultUnit;
    }
}