package com.weihua.rpc.core.server.provider;

import com.weihua.rpc.core.server.config.ServerConfig;
import com.weihua.rpc.core.server.registry.ServiceRegistry;
import com.weihua.rpc.core.server.ratelimit.RateLimitProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务提供者
 * 管理服务实例和注册服务到注册中心
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rpc.mode", havingValue = "server", matchIfMissing = false)
public class ServiceProvider {

    // 服务实例映射表
    private final Map<String, Object> serviceInstances = new HashMap<>();

    // 限流提供者
    @Autowired
    private RateLimitProvider rateLimitProvider;

    // 服务注册中心
    @Autowired
    private ServiceRegistry serviceRegistry;

    // 服务配置
    @Autowired
    private ServerConfig serverConfig;

    // 服务地址
    private InetSocketAddress serviceAddress;

    @PostConstruct
    public void init() {
        // 初始化服务地址
        this.serviceAddress = new InetSocketAddress(
                serverConfig.getHost(),
                serverConfig.getPort());

        log.info("服务提供者初始化完成，服务地址: {}:{}",
                serverConfig.getHost(), serverConfig.getPort());
    }

    /**
     * 注册服务实例
     * 
     * @param serviceInterface 服务接口类
     * @param serviceInstance  服务实例
     */
    public void registerService(Class<?> serviceInterface, Object serviceInstance) {
        String serviceName = serviceInterface.getName();

        // 存储服务实例
        serviceInstances.put(serviceName, serviceInstance);

        // 注册到服务中心
        serviceRegistry.register(serviceInterface, serviceAddress);

        log.info("注册服务: {}, 实例: {}", serviceName, serviceInstance.getClass().getName());
    }

    /**
     * 获取服务实例
     * 
     * @param serviceName 服务名称
     * @return 服务实例，不存在则返回null
     */
    public Object getService(String serviceName) {
        return serviceInstances.get(serviceName);
    }

    /**
     * 获取限流提供者
     * 
     * @return 限流提供者
     */
    public RateLimitProvider getRateLimitProvider() {
        return rateLimitProvider;
    }

    /**
     * 获取已注册服务数量
     * 
     * @return 服务数量
     */
    public int getServiceCount() {
        return serviceInstances.size();
    }

    /**
     * 关闭并释放资源
     */
    @PreDestroy
    public void shutdown() {
        // 关闭服务注册中心
        if (serviceRegistry != null) {
            try {
                serviceRegistry.shutdown();
                log.info("服务注册中心已关闭");
            } catch (Exception e) {
                log.error("关闭服务注册中心时发生异常", e);
            }
        }

        // 清空服务实例
        serviceInstances.clear();
        log.info("服务提供者已关闭");
    }
}
