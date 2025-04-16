/*
 * @Author: weihua hu
 * @Date: 2025-04-15 01:31:02
 * @LastEditTime: 2025-04-15 01:50:45
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.configurer;

import com.weihua.rpc.core.client.config.DiscoveryConfig;
import com.weihua.rpc.core.condition.ConditionalOnClientMode;
import com.weihua.rpc.springboot.properties.RpcRegistryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 服务发现配置绑定器
 * 负责将SpringBoot配置属性绑定到核心配置对象
 */
@Configuration
@ConditionalOnClientMode
@EnableConfigurationProperties(RpcRegistryProperties.class)
public class DiscoveryConfigurer {

    /**
     * 创建服务发现配置对象并绑定属性
     * 
     * @param properties 注册中心配置属性
     * @param env        环境变量，用于获取额外的配置参数
     * @return 服务发现配置对象
     */
    @Bean
    public DiscoveryConfig discoveryConfig(RpcRegistryProperties properties, Environment env) {
        DiscoveryConfig config = new DiscoveryConfig();

        // 基础属性映射
        config.setType(properties.getType());
        config.setAddress(properties.getAddress());
        config.setConnectTimeout(properties.getConnectTimeout());

        // 检查环境变量中是否有其他配置属性
        Integer timeout = env.getProperty("rpc.discovery.timeout", Integer.class);
        if (timeout != null) {
            config.setTimeout(timeout);
        }

        Integer retryTimes = env.getProperty("rpc.discovery.retry-times", Integer.class);
        if (retryTimes != null) {
            config.setRetryTimes(retryTimes);
        }

        Integer syncPeriod = env.getProperty("rpc.discovery.sync-period", Integer.class);
        if (syncPeriod != null) {
            config.setSyncPeriod(syncPeriod);
        }

        Boolean healthCheckEnabled = env.getProperty("rpc.discovery.health-check-enabled", Boolean.class);
        if (healthCheckEnabled != null) {
            config.setHealthCheckEnabled(healthCheckEnabled);
        }

        Integer healthCheckInterval = env.getProperty("rpc.discovery.health-check-interval", Integer.class);
        if (healthCheckInterval != null) {
            config.setHealthCheckInterval(healthCheckInterval);
        }

        Boolean metadataCache = env.getProperty("rpc.discovery.metadata-cache", Boolean.class);
        if (metadataCache != null) {
            config.setMetadataCache(metadataCache);
        }

        Integer metadataExpireSeconds = env.getProperty("rpc.discovery.metadata-expire-seconds", Integer.class);
        if (metadataExpireSeconds != null) {
            config.setMetadataExpireSeconds(metadataExpireSeconds);
        }

        return config;
    }
}