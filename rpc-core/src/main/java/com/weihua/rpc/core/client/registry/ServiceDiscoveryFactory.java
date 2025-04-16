package com.weihua.rpc.core.client.registry;

import com.weihua.rpc.common.extension.ExtensionLoader;
import com.weihua.rpc.core.client.cache.ServiceAddressCache;
import com.weihua.rpc.core.client.config.DiscoveryConfig;
import com.weihua.rpc.core.client.invoker.InvokerManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务发现工厂
 * 负责创建和管理ServiceDiscovery实例
 */
@Slf4j
public class ServiceDiscoveryFactory {

    private static final ExtensionLoader<ServiceDiscovery> LOADER = ExtensionLoader
            .getExtensionLoader(ServiceDiscovery.class);

    private static volatile ServiceDiscovery instance;

    /**
     * 获取服务发现实例
     * 
     * @param config         发现配置
     * @param addressCache   地址缓存
     * @param invokerManager Invoker管理器
     * @return 服务发现实例
     */
    public static ServiceDiscovery getServiceDiscovery(
            DiscoveryConfig config,
            ServiceAddressCache addressCache,
            InvokerManager invokerManager) {
        if (instance == null) {
            synchronized (ServiceDiscoveryFactory.class) {
                if (instance == null) {
                    String type = config.getType();
                    try {
                        // 通过SPI加载实现
                        ServiceDiscovery discovery = LOADER.getExtension(type);

                        // 配置适配器
                        if (discovery instanceof AbstractServiceDiscovery) {
                            AbstractServiceDiscovery abstractDiscovery = (AbstractServiceDiscovery) discovery;
                            abstractDiscovery.setDiscoveryConfig(config);
                            abstractDiscovery.setAddressCache(addressCache);
                            abstractDiscovery.setInvokerManager(invokerManager);
                            abstractDiscovery.init();
                        }

                        instance = discovery;
                        log.info("创建服务发现中心: {}, 地址: {}", type, config.getAddress());
                    } catch (Exception e) {
                        log.error("创建服务发现中心失败: {}", type, e);
                        // 回退到默认实现
                        instance = createDefaultDiscovery(config, addressCache, invokerManager);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * 创建默认的服务发现中心
     */
    private static ServiceDiscovery createDefaultDiscovery(
            DiscoveryConfig config,
            ServiceAddressCache addressCache,
            InvokerManager invokerManager) {
        try {
            ServiceDiscovery discovery = LOADER.getDefaultExtension();

            if (discovery instanceof AbstractServiceDiscovery) {
                AbstractServiceDiscovery abstractDiscovery = (AbstractServiceDiscovery) discovery;
                abstractDiscovery.setDiscoveryConfig(config);
                abstractDiscovery.setAddressCache(addressCache);
                abstractDiscovery.setInvokerManager(invokerManager);
                abstractDiscovery.init();
            }

            log.info("使用默认服务发现中心: {}", discovery.getClass().getSimpleName());
            return discovery;
        } catch (Exception e) {
            log.error("创建默认服务发现中心失败", e);
            throw new RuntimeException("无法创建服务发现中心", e);
        }
    }

    /**
     * 关闭当前发现中心
     */
    public static void shutdown() {
        if (instance != null) {
            try {
                instance.close();
                log.info("关闭服务发现中心: {}", instance.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("关闭服务发现中心失败", e);
            } finally {
                instance = null;
            }
        }
    }
}