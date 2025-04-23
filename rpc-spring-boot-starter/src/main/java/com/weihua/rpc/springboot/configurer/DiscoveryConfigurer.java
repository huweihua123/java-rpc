/*
 * @Author: weihua hu
 * @Date: 2025-04-15 01:31:02
 * @LastEditTime: 2025-04-23 16:06:54
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

import java.time.Duration;

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
        config.setTimeout(properties.getTimeout());
        config.setRetryTimes(properties.getRetryTimes());
        config.setSyncPeriod(properties.getSyncPeriod());
        config.setHealthCheckEnabled(properties.isHealthCheckEnabled());
        config.setHealthCheckInterval(properties.getHealthCheckPeriod());

        // 检查环境变量中是否有其他配置属性
        Duration discoveryTimeout = env.getProperty("rpc.discovery.timeout", Duration.class);
        if (discoveryTimeout != null) {
            config.setTimeout(discoveryTimeout);
        }

        Integer retryTimes = env.getProperty("rpc.discovery.retry-times", Integer.class);
        if (retryTimes != null) {
            config.setRetryTimes(retryTimes);
        }

        Duration syncPeriod = env.getProperty("rpc.discovery.sync-period", Duration.class);
        if (syncPeriod != null) {
            config.setSyncPeriod(syncPeriod);
        }

        Boolean healthCheckEnabled = env.getProperty("rpc.discovery.health-check-enabled", Boolean.class);
        if (healthCheckEnabled != null) {
            config.setHealthCheckEnabled(healthCheckEnabled);
        }

        Duration healthCheckInterval = env.getProperty("rpc.discovery.health-check-interval", Duration.class);
        if (healthCheckInterval != null) {
            config.setHealthCheckInterval(healthCheckInterval);
        }

        Boolean metadataCache = env.getProperty("rpc.discovery.metadata-cache", Boolean.class);
        if (metadataCache != null) {
            config.setMetadataCache(metadataCache);
        }

        Duration metadataExpireTime = env.getProperty("rpc.discovery.metadata-expire-time", Duration.class);
        if (metadataExpireTime != null) {
            config.setMetadataExpireTime(metadataExpireTime);
        }

        return config;
    }
}