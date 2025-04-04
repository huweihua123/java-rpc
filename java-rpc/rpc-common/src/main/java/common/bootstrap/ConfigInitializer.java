/*
 * @Author: weihua hu
 * @Date: 2025-04-04 21:56:08
 * @LastEditTime: 2025-04-04 21:56:09
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.bootstrap;

import common.config.ConfigRefreshManager;
import common.config.ConfigurationManager;
import lombok.extern.log4j.Log4j2;

/**
 * 配置初始化器
 * 用于在应用启动时初始化配置系统
 */
@Log4j2
public class ConfigInitializer {

    /**
     * 初始化提供者配置
     */
    public static void initializeProvider() {
        ConfigurationManager.getInstance().initialize(ConfigurationManager.Role.PROVIDER);
        // 初始化配置刷新管理器
        ConfigRefreshManager.getInstance();
        log.info("服务提供者配置系统初始化完成");
    }

    /**
     * 初始化消费者配置
     */
    public static void initializeConsumer() {
        ConfigurationManager.getInstance().initialize(ConfigurationManager.Role.CONSUMER);
        // 初始化配置刷新管理器
        ConfigRefreshManager.getInstance();
        log.info("服务消费者配置系统初始化完成");
    }
}