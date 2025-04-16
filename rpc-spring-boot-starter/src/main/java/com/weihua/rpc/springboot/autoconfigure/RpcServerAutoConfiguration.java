/*
 * @Author: weihua hu
 * @Date: 2025-04-15 02:31:17
 * @LastEditTime: 2025-04-15 01:45:49
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.springboot.autoconfigure;

import com.weihua.rpc.core.condition.ConditionalOnServerMode;
import com.weihua.rpc.spring.config.RpcServerConfiguration;
import com.weihua.rpc.spring.processor.RpcServiceBeanPostProcessor;
import com.weihua.rpc.springboot.configurer.RateLimitConfigurer;
import com.weihua.rpc.springboot.configurer.RegistryConfigurer;
import com.weihua.rpc.springboot.configurer.ServerConfigurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC服务端自动配置类
 * 仅在服务端模式下加载
 */
@Configuration
@ConditionalOnServerMode
@Import({
        RpcServerConfiguration.class,  // 导入核心服务端配置
        ServerConfigurer.class,        // 服务器配置绑定器
        RegistryConfigurer.class,       // 服务注册配置绑定器
        RateLimitConfigurer.class  // 限流配置绑定器
})
public class RpcServerAutoConfiguration {

    /**
     * 注册RPC服务注解处理器
     */
    @Bean
    public RpcServiceBeanPostProcessor rpcServiceBeanPostProcessor() {
        return new RpcServiceBeanPostProcessor();
    }
}