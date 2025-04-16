/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:30:48
 * @LastEditTime: 2025-04-15 15:26:28
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.configurer;

import com.weihua.rpc.core.condition.ConditionalOnServerMode;
import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import com.weihua.rpc.core.server.config.RateLimitConfig;
import com.weihua.rpc.springboot.properties.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置绑定器
 */
@Configuration
@ConditionalOnServerMode
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfigurer {

    /**
     * 创建限流配置对象并绑定属性
     */
    @Bean
    public RateLimitConfig rateLimitConfig(RateLimitProperties properties) {
        RateLimitConfig config = new RateLimitConfig();

        // 基础配置
        config.setEnabled(properties.isEnabled());
        config.setDefaultQps(properties.getDefaultQps());
        config.setDefaultStrategy(properties.getDefaultStrategy());
        config.setAdaptiveQps(properties.isAdaptiveQps());
        config.setMaxServiceQps(properties.getMaxServiceQps());
        config.setMaxIpQps(properties.getMaxIpQps());
        config.setBurstCapacity(properties.getBurstCapacity());

        // 接口级别配置
        Map<String, Integer> interfaceQpsMap = new HashMap<>();
        Map<String, Strategy> interfaceStrategyMap = new HashMap<>();

        properties.getInterfaces().forEach((interfaceName, interfaceConfig) -> {
            interfaceQpsMap.put(interfaceName, interfaceConfig.getQps());
            interfaceStrategyMap.put(interfaceName, interfaceConfig.getStrategy());
        });

        config.setInterfaceQpsConfig(interfaceQpsMap);
        config.setInterfaceStrategyConfig(interfaceStrategyMap);

        // 方法级别配置
        Map<String, Integer> methodQpsMap = new HashMap<>();
        Map<String, Strategy> methodStrategyMap = new HashMap<>();

        properties.getMethods().forEach((methodName, methodConfig) -> {
            methodQpsMap.put(methodName, methodConfig.getQps());
            methodStrategyMap.put(methodName, methodConfig.getStrategy());
        });

        config.setMethodQpsConfig(methodQpsMap);
        config.setMethodStrategyConfig(methodStrategyMap);

        return config;
    }
}