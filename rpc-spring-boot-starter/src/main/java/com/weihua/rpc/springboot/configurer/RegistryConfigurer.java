/*
 * @Author: weihua hu
 * @Date: 2025-04-15 01:30:52
 * @LastEditTime: 2025-04-15 01:50:55
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.configurer;

import com.weihua.rpc.core.condition.ConditionalOnServerMode;
import com.weihua.rpc.core.server.config.RegistryConfig;
import com.weihua.rpc.springboot.properties.RpcRegistryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册中心配置绑定器
 * 负责将SpringBoot配置属性绑定到核心配置对象
 */
@Configuration
@ConditionalOnServerMode
@EnableConfigurationProperties(RpcRegistryProperties.class)
public class RegistryConfigurer {

    /**
     * 创建注册中心配置对象并绑定属性
     * 
     * @param properties 配置属性
     * @return 注册中心配置对象
     */
    @Bean
    public RegistryConfig registryConfig(RpcRegistryProperties properties) {
        RegistryConfig config = new RegistryConfig();

        // 将Properties属性复制到Config对象
        config.setType(properties.getType());
        config.setAddress(properties.getAddress());
        config.setConnectTimeout(properties.getConnectTimeout());

        // 设置其他属性，可以根据需要从properties扩展
        if (properties.getTimeout() > 0) {
            config.setTimeout(properties.getTimeout());
        }

        if (properties.getRetryTimes() > 0) {
            config.setRetryTimes(properties.getRetryTimes());
        }

        if (properties.getHealthCheckPeriod() > 0) {
            config.setHealthCheckPeriod(properties.getHealthCheckPeriod());
        }

        if (properties.getCheckInterval() > 0) {
            config.setCheckInterval(properties.getCheckInterval());
        }

        if (properties.getCheckTimeout() > 0) {
            config.setCheckTimeout(properties.getCheckTimeout());
        }

        if (properties.getDeregisterTime() != null && !properties.getDeregisterTime().isEmpty()) {
            config.setDeregisterTime(properties.getDeregisterTime());
        }

        return config;
    }
}