package com.weihua.client.config;

import common.config.ConfigRefreshManager;
import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * 客户端配置管理类
 * 集中管理RPC客户端相关配置
 */
@Log4j2
public class ClientConfigManager implements ConfigRefreshManager.ConfigurableComponent {
    // 单例实例
    private static final ClientConfigManager INSTANCE = new ClientConfigManager();
    private final ConfigurationManager configManager;

    // 网络连接配置
    @Getter
    private int connectTimeout;
    @Getter
    private int requestTimeout;
    @Getter
    private int maxConnectionsPerAddress;
    @Getter
    private int initConnectionsPerAddress;

    // 重试配置
    @Getter
    private boolean retryEnable;
    @Getter
    private int maxRetries;
    @Getter
    private long retryIntervalMillis;
    @Getter
    private boolean retryOnlyIdempotent;

    // 心跳配置
    @Getter
    private int heartbeatInterval;
    @Getter
    private int heartbeatTimeout;

    private ClientConfigManager() {
        this.configManager = ConfigurationManager.getInstance();
        loadConfig();
        // 注册到配置刷新管理器
        ConfigRefreshManager.getInstance().register(this);
    }

    public static ClientConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * 加载配置项
     */
    private void loadConfig() {
        // 网络连接配置
        connectTimeout = configManager.getInt("rpc.client.connect.timeout", 3000);
        requestTimeout = configManager.getInt("rpc.client.request.timeout", 5);
        maxConnectionsPerAddress = configManager.getInt("rpc.client.connections.max", 4);
        initConnectionsPerAddress = configManager.getInt("rpc.client.connections.init", 1);

        // 重试配置
        retryEnable = configManager.getBoolean("rpc.client.retry.enable", true);
        maxRetries = configManager.getInt("rpc.client.retry.max", 2);
        retryIntervalMillis = configManager.getLong("rpc.client.retry.interval", 1000);
        retryOnlyIdempotent = configManager.getBoolean("rpc.client.retry.only.idempotent", true);

        // 心跳配置
        heartbeatInterval = configManager.getInt("rpc.client.heartbeat.interval", 30);
        heartbeatTimeout = configManager.getInt("rpc.client.heartbeat.timeout", 5);

        log.info("已加载客户端配置: 连接超时={}ms, 请求超时={}s, 最大连接数={}, 初始连接数={}, 重试={}",
                connectTimeout, requestTimeout, maxConnectionsPerAddress, initConnectionsPerAddress,
                retryEnable ? "启用(最大" + maxRetries + "次)" : "禁用");
    }

    /**
     * 刷新配置
     */
    @Override
    public void refreshConfig() {
        loadConfig();
        log.info("已刷新客户端配置");
    }

    /**
     * 获取特定接口的请求超时时间(秒)
     */
    public int getInterfaceRequestTimeout(String interfaceName) {
        String key = "rpc.client.interfaces." + interfaceName + ".request.timeout";
        return configManager.getInt(key, requestTimeout);
    }

    /**
     * 获取特定接口的最大重试次数
     */
    public int getInterfaceMaxRetries(String interfaceName) {
        String key = "rpc.client.interfaces." + interfaceName + ".retry.max";
        return configManager.getInt(key, maxRetries);
    }
}
