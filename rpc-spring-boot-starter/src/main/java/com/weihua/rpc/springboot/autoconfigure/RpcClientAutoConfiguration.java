/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:36:33
 * @LastEditTime: 2025-04-10 02:36:34
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.springboot.autoconfigure;

import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.config.DiscoveryConfig;
import com.weihua.rpc.spring.config.RpcClientConfiguration;
import com.weihua.rpc.springboot.listener.RpcClientInitListener;
import com.weihua.rpc.springboot.properties.RpcClientProperties;
import com.weihua.rpc.springboot.properties.RpcRegistryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC客户端自动配置类
 */
@Configuration
@ConditionalOnClass(ClientConfig.class)
@EnableConfigurationProperties({RpcClientProperties.class, RpcRegistryProperties.class})
@Import(RpcClientConfiguration.class)
//@ConditionalOnProperty(prefix = "rpc.client", name = "enabled", havingValue = "true", matchIfMissing = true)
//@ConditionalOnProperty(name = "rpc.mode", havingValue = "client", matchIfMissing = false)
@ConditionalOnProperty(
        prefix = "rpc",
        value = {
                "client.enabled=true",
                "mode=client"
        },
        matchIfMissing = false
)
public class RpcClientAutoConfiguration {

    @Autowired
    private RpcClientProperties clientProperties;

    @Autowired
    private RpcRegistryProperties registryProperties;

    /**
     * 配置客户端配置Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientConfig clientConfig() {
        ClientConfig config = new ClientConfig();

        // 从属性中注入配置
        config.setTimeout(clientProperties.getTimeout());
        config.setConnectTimeout(clientProperties.getConnectTimeout());
        config.setMaxRetries(clientProperties.getRetries());
        config.setRetryEnable(clientProperties.isRetryEnable());
        config.setRetryIntervalMillis(clientProperties.getRetryInterval());
        config.setServiceVersion(clientProperties.getServiceVersion());
        config.setServiceGroup(clientProperties.getServiceGroup());

        // 熔断器配置
        config.setCircuitBreakerEnable(clientProperties.isCircuitBreakerEnable());

        return config;
    }

    /**
     * 配置注册中心配置Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public DiscoveryConfig registryConfig() {
        DiscoveryConfig config = new DiscoveryConfig();

        // 从属性中注入配置
        config.setType(registryProperties.getType());
        config.setAddress(registryProperties.getAddress());
        config.setConnectTimeout(registryProperties.getConnectTimeout());
        config.setTimeout(registryProperties.getTimeout());
        config.setRetryTimes(registryProperties.getRetryTimes());
        config.setSyncPeriod(registryProperties.getSyncPeriod());

        return config;
    }

    /**
     * 客户端初始化监听器
     */
    @Bean
    public RpcClientInitListener rpcClientInitListener() {
        return new RpcClientInitListener();
    }
}
