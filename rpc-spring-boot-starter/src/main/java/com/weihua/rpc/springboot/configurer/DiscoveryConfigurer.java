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
        // 1. 保留原有属性映射
        Duration discoveryTimeout = env.getProperty("rpc.discovery.timeout", Duration.class);
        if (discoveryTimeout != null) {
            config.setTimeout(discoveryTimeout);
        }

        Integer retryTimes = env.getProperty("rpc.discovery.retry-times", Integer.class);
        if (retryTimes != null) {
            config.setRetryTimes(retryTimes);
        }

        // 2. 更新sync-interval属性名与YAML保持一致
        Duration syncInterval = env.getProperty("rpc.discovery.sync-interval", Duration.class);
        if (syncInterval != null) {
            config.setSyncPeriod(syncInterval);
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

        // 3. 新增故障保护配置映射
        Boolean faultToleranceEnabled = env.getProperty("rpc.discovery.fault-tolerance.enabled", Boolean.class);
        if (faultToleranceEnabled != null) {
            config.setFaultToleranceEnabled(faultToleranceEnabled);
        }

        String faultToleranceMode = env.getProperty("rpc.discovery.fault-tolerance.mode", String.class);
        if (faultToleranceMode != null) {
            config.setFaultToleranceMode(faultToleranceMode);
        }

        return config;
    }
}