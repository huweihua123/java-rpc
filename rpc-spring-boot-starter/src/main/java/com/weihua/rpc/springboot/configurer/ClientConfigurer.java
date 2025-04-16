/*
 * @Author: weihua hu
 * @Date: 2025-04-15 01:24:29
 * @LastEditTime: 2025-04-15 02:33:21
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.springboot.configurer;

import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.condition.ConditionalOnClientMode;
import com.weihua.rpc.springboot.properties.CircuitBreakerProperties;
import com.weihua.rpc.springboot.properties.RpcClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 客户端配置绑定器
 */
@Configuration
@ConditionalOnClientMode
@EnableConfigurationProperties({
        RpcClientProperties.class,
        CircuitBreakerProperties.class
})
public class ClientConfigurer {

    @Bean
    public ClientConfig clientConfig(
            RpcClientProperties properties) {

        ClientConfig config = new ClientConfig();

        // 基础超时配置
        config.setTimeout(properties.getTimeout());
        config.setConnectTimeout(properties.getConnectTimeout());
        config.setRequestTimeout(properties.getRequestTimeout());

        // 连接管理配置
        config.setMaxConnectionsPerAddress(properties.getMaxConnectionsPerAddress());
        config.setInitConnectionsPerAddress(properties.getInitConnectionsPerAddress());

        // 重试配置
        config.setRetryEnable(properties.isRetryEnable());
        config.setMaxRetryAttempts(properties.getMaxRetries());
        config.setRetryIntervalMillis(properties.getRetryInterval());
        config.setRetryOnlyIdempotent(properties.isRetryOnlyIdempotent());

        // 退避策略配置
        config.setBackoffMultiplier(properties.getBackoffMultiplier());
        config.setMaxBackoffTime(properties.getMaxBackoffTime());
        config.setAddJitter(properties.isAddJitter());
        config.setMinRetryInterval(properties.getMinRetryInterval());

        // 连接模式配置
        config.setConnectionMode(properties.getConnectionMode());

        // 心跳配置
        config.setHeartbeatInterval(properties.getHeartbeatInterval());
        config.setHeartbeatTimeout(properties.getHeartbeatTimeout());

        // 服务配置
        config.setLoadBalanceStrategy(properties.getLoadBalance());
        config.setServiceVersion(properties.getServiceVersion());
        config.setServiceGroup(properties.getServiceGroup());

        return config;
    }
}