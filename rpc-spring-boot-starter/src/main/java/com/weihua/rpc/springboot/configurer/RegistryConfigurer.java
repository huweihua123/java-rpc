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

        config.setTimeout(properties.getTimeout());

        config.setRetryTimes(properties.getRetryTimes());

        config.setHealthCheckPeriod(properties.getHealthCheckPeriod());

        config.setCheckInterval(properties.getCheckInterval());
        config.setCheckTimeout(properties.getCheckTimeout());

        config.setDeregisterTime(properties.getDeregisterTime());

        return config;
    }
}