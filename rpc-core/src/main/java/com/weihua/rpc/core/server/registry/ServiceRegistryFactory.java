/*
 * @Author: weihua hu
 * @Date: 2025-04-15 00:25:49
 * @LastEditTime: 2025-04-15 00:25:51
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.registry;

import com.weihua.rpc.common.extension.ExtensionLoader;
import com.weihua.rpc.core.server.config.RegistryConfig;
import com.weihua.rpc.core.server.registry.impl.LocalServiceRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务注册工厂
 * 负责创建和管理ServiceRegistry实例
 */
@Slf4j
public class ServiceRegistryFactory {

    private static final ExtensionLoader<ServiceRegistry> LOADER = ExtensionLoader
            .getExtensionLoader(ServiceRegistry.class);

    private static volatile ServiceRegistry instance;

    /**
     * 获取服务注册实例
     * 
     * @param config 注册中心配置
     * @return 服务注册实例
     */
    public static ServiceRegistry getServiceRegistry(RegistryConfig config) {
        if (instance == null) {
            synchronized (ServiceRegistryFactory.class) {
                if (instance == null) {
                    String type = config.getType();
                    try {
                        // 通过SPI加载实现
                        ServiceRegistry registry = LOADER.getExtension(type);

                        // 配置适配器
                        if (registry instanceof AbstractServiceRegistry) {
                            ((AbstractServiceRegistry) registry).setRegistryConfig(config);
                            ((AbstractServiceRegistry) registry).init();
                        }

                        instance = registry;
                        log.info("创建服务注册中心: {}, 地址: {}", type, config.getAddress());
                    } catch (Exception e) {
                        log.error("创建服务注册中心失败: {}", type, e);
                        // 回退到默认实现
                        instance = createDefaultRegistry(config);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * 创建默认的注册中心
     */
    private static ServiceRegistry createDefaultRegistry(RegistryConfig config) {
        try {
            ServiceRegistry registry = LOADER.getDefaultExtension();

            if (registry instanceof AbstractServiceRegistry) {
                ((AbstractServiceRegistry) registry).setRegistryConfig(config);
                ((AbstractServiceRegistry) registry).init();
            }

            log.info("使用默认服务注册中心: {}", registry.getClass().getSimpleName());
            return registry;
        } catch (Exception e) {
            log.error("创建默认服务注册中心失败", e);
            // 最后回退到本地注册
            LocalServiceRegistry localRegistry = new LocalServiceRegistry();
            log.info("回退到本地服务注册");
            return localRegistry;
        }
    }

    /**
     * 关闭当前注册中心
     */
    public static void shutdown() {
        if (instance != null) {
            try {
                instance.shutdown();
                log.info("关闭服务注册中心: {}", instance.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("关闭服务注册中心失败", e);
            } finally {
                instance = null;
            }
        }
    }
}