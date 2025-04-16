/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:33:00
 * @LastEditTime: 2025-04-16 13:39:02
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.spring.config;

import com.weihua.rpc.core.server.RpcServer;
import com.weihua.rpc.core.server.config.RateLimitConfig;
import com.weihua.rpc.core.server.config.RegistryConfig;
import com.weihua.rpc.core.server.config.ServerConfig;
import com.weihua.rpc.core.server.netty.NettyRpcServer;
import com.weihua.rpc.core.server.provider.ServiceProvider;
import com.weihua.rpc.core.server.ratelimit.RateLimitManager;
import com.weihua.rpc.core.server.ratelimit.RateLimitProvider;
import com.weihua.rpc.core.server.registry.ServiceRegistry;
import com.weihua.rpc.core.server.registry.ServiceRegistryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import javax.annotation.PreDestroy;

/**
 * RPC服务端配置类
 * 使用显式Bean定义代替组件扫描
 */
@Configuration
@Slf4j
public class RpcServerConfiguration {
    @Lazy
    @Autowired
    private RpcServer rpcServer;

    @Autowired
    private ServerConfig serverConfig;

    public RpcServerConfiguration() {
        log.info("RPC服务端配置已初始化");
    }

    /**
     * 限流提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitProvider rateLimitProvider(RateLimitConfig rateLimitConfig) {
        return new RateLimitProvider(rateLimitConfig);
    }

    /**
     * 限流管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitManager rateLimitManager(RateLimitConfig rateLimitConfig) {
        return new RateLimitManager(rateLimitConfig);
    }

    /**
     * 服务提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceProvider serviceProvider() {
        return new ServiceProvider();
    }

    /**
     * RPC服务器
     * 使用NettyRpcServer实现，并通过方法参数注入依赖
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcServer rpcServer(ServerConfig serverConfig, ServiceProvider serviceProvider) {
        return new NettyRpcServer(serverConfig, serviceProvider);
    }

    /**
     * 服务注册中心
     * 使用工厂创建基于SPI的注册中心实例
     */
    @Bean
    @ConditionalOnMissingBean(ServiceRegistry.class)
    public ServiceRegistry serviceRegistry(RegistryConfig registryConfig) {
        return ServiceRegistryFactory.getServiceRegistry(registryConfig);
    }

    /**
     * 当Spring上下文刷新完成后启动RPC服务器
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保是根上下文才启动服务器
        if (event.getApplicationContext().getParent() == null) {
            try {
                // 启动RPC服务器
                if (!rpcServer.isRunning()) {
                    rpcServer.start();
                    log.info("RPC服务器已启动，监听地址: {}:{}",
                            serverConfig.getHost(), serverConfig.getPort());
                }
            } catch (Exception e) {
                log.error("RPC服务器启动失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 关闭RPC服务器
     */
    @PreDestroy
    public void stopRpcServer() {
        if (rpcServer != null && rpcServer.isRunning()) {
            rpcServer.stop();
            log.info("RPC服务器已关闭");
        }
    }
}