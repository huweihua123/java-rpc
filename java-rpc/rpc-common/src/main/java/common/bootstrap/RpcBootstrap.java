/*
 * @Author: weihua hu
 * @Date: 2025-04-04 01:21:57
 * @LastEditTime: 2025-04-04 01:22:21
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.bootstrap;

import common.config.ConfigurationManager;
import lombok.extern.log4j.Log4j2;

/**
 * RPC框架引导类，负责初始化框架
 */
@Log4j2
public class RpcBootstrap {
    private static volatile boolean initialized = false;

    /**
     * 初始化提供者
     */
    public static synchronized void initializeProvider() {
        if (initialized) {
            log.warn("RPC framework has already been initialized");
            return;
        }

        log.info("Initializing RPC provider...");
        ConfigurationManager.getInstance().initialize(ConfigurationManager.Role.PROVIDER);
        initialized = true;
        log.info("RPC provider initialized successfully");
    }

    /**
     * 初始化消费者
     */
    public static synchronized void initializeConsumer() {
        if (initialized) {
            log.warn("RPC framework has already been initialized");
            return;
        }

        log.info("Initializing RPC consumer...");
        ConfigurationManager.getInstance().initialize(ConfigurationManager.Role.CONSUMER);
        initialized = true;
        log.info("RPC consumer initialized successfully");
    }

    /**
     * 初始化通用框架（不加载提供者或消费者特定配置）
     */
    public static synchronized void initialize() {
        if (initialized) {
            log.warn("RPC framework has already been initialized");
            return;
        }

        log.info("Initializing RPC framework...");
        ConfigurationManager.getInstance().initialize(ConfigurationManager.Role.COMMON);
        initialized = true;
        log.info("RPC framework initialized successfully");
    }

    /**
     * 检查框架是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
