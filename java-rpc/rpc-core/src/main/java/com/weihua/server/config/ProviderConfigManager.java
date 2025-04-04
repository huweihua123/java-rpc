/*
 * @Author: weihua hu
 * @Date: 2025-04-04 21:50:08
 * @LastEditTime: 2025-04-04 21:50:48
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.server.config;

import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * 服务提供者配置管理类
 */
@Log4j2
public class ProviderConfigManager {
    private static final ProviderConfigManager INSTANCE = new ProviderConfigManager();
    private final ConfigurationManager configManager;

    // 服务器配置
    @Getter
    private String host;
    @Getter
    private int port;
    @Getter
    private int ioThreads;
    @Getter
    private int workerThreads;
    @Getter
    private int maxConnections;
    @Getter
    private int connectionIdleTime;

    // 服务治理
    @Getter
    private boolean dynamicRegister;
    @Getter
    private boolean delayRegister;
    @Getter
    private int delayRegisterTime;
    @Getter
    private boolean metadataEnabled;

    private ProviderConfigManager() {
        this.configManager = ConfigurationManager.getInstance();
        loadConfig();
    }

    public static ProviderConfigManager getInstance() {
        return INSTANCE;
    }

    private void loadConfig() {
        // 服务器配置
        host = configManager.getString("rpc.provider.host", "127.0.0.1");
        port = configManager.getInt("rpc.provider.port", 9999);
        ioThreads = configManager.getInt("rpc.provider.io.threads", Runtime.getRuntime().availableProcessors());
        workerThreads = configManager.getInt("rpc.provider.worker.threads", 200);
        maxConnections = configManager.getInt("rpc.provider.max.connections", 10000);
        connectionIdleTime = configManager.getInt("rpc.provider.connection.idle.time", 300000);

        // 服务治理
        dynamicRegister = configManager.getBoolean("rpc.provider.dynamic", true);
        delayRegister = configManager.getBoolean("rpc.provider.delay.register", false);
        delayRegisterTime = configManager.getInt("rpc.provider.delay.register.time", 5000);
        metadataEnabled = configManager.getBoolean("rpc.provider.metadata.enabled", true);
    }

    public void refresh() {
        loadConfig();
        log.info("已刷新服务提供者配置");
    }
}