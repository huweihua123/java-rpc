/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:36:53
 * @LastEditTime: 2025-04-10 02:36:55
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.autoconfigure;

import com.weihua.rpc.core.server.RpcServer;
import com.weihua.rpc.core.server.config.RegistryConfig;
import com.weihua.rpc.core.server.config.ServerConfig;
import com.weihua.rpc.spring.config.RpcServerConfiguration;
import com.weihua.rpc.springboot.listener.RpcServerStartListener;
import com.weihua.rpc.springboot.properties.RpcRegistryProperties;
import com.weihua.rpc.springboot.properties.RpcServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC服务端自动配置类
 */
@Configuration
@ConditionalOnClass(RpcServer.class)
@EnableConfigurationProperties({ RpcServerProperties.class, RpcRegistryProperties.class })
@Import(RpcServerConfiguration.class)
@ConditionalOnProperty(prefix = "rpc.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RpcServerAutoConfiguration {

    @Autowired
    private RpcServerProperties serverProperties;

    @Autowired
    private RpcRegistryProperties registryProperties;

    /**
     * 配置服务端配置Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ServerConfig serverConfig() {
        ServerConfig config = new ServerConfig();

        // 从属性中注入配置
        config.setHost(serverProperties.getHost());
        config.setPort(serverProperties.getPort());
        config.setIoThreads(serverProperties.getIoThreads());
        config.setWorkerThreads(serverProperties.getWorkerThreads());
        config.setMaxConnections(serverProperties.getMaxConnections());
        config.setReaderIdleTime(serverProperties.getReaderIdleTime());
        config.setWriterIdleTime(serverProperties.getWriterIdleTime());
        config.setAllIdleTime(serverProperties.getAllIdleTime());
        config.setRequestTimeout(serverProperties.getRequestTimeout());

        return config;
    }

    /**
     * 配置注册中心配置Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public RegistryConfig registryConfig() {
        RegistryConfig config = new RegistryConfig();

        // 从属性中注入配置
        config.setType(registryProperties.getType());
        config.setAddress(registryProperties.getAddress());
        config.setConnectTimeout(registryProperties.getConnectTimeout());
        config.setTimeout(registryProperties.getTimeout());
        config.setRetryTimes(registryProperties.getRetryTimes());
        config.setHealthCheckPeriod(registryProperties.getHealthCheckPeriod());

        return config;
    }

    /**
     * 如果设置为自动启动，则配置服务端启动监听器
     */
    @Bean
    @ConditionalOnBean(RpcServer.class)
    @ConditionalOnProperty(prefix = "rpc.server", name = "auto-start", havingValue = "true", matchIfMissing = true)
    public RpcServerStartListener rpcServerStartListener() {
        return new RpcServerStartListener();
    }
}
