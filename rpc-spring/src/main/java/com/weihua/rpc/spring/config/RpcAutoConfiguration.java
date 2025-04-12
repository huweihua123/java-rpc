/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:33:24
 * @LastEditTime: 2025-04-12 14:02:43
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.config;

import com.weihua.rpc.core.condition.ConditionalOnClientMode;
import com.weihua.rpc.core.condition.ConditionalOnServerMode;
import com.weihua.rpc.spring.processor.RpcReferenceBeanPostProcessor;
import com.weihua.rpc.spring.processor.RpcServiceBeanPostProcessor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC自动配置类
 * 集成RPC的客户端和服务端配置
 */
@Configuration
@Import({
        RpcClientConfiguration.class,
        RpcServerConfiguration.class
})
public class RpcAutoConfiguration {

    /**
     * 注册RPC服务注解处理器
     */
    @Bean
    @ConditionalOnServerMode
    // @ConditionalOnProperty(name = "rpc.mode", havingValue = "server", matchIfMissing = false)
    public RpcServiceBeanPostProcessor rpcServiceBeanPostProcessor() {
        return new RpcServiceBeanPostProcessor();
    }

    /**
     * 注册RPC引用注解处理器
     */
    @Bean
    // @ConditionalOnProperty(name = "rpc.mode", havingValue = "client", matchIfMissing = false)
    @ConditionalOnClientMode
    public RpcReferenceBeanPostProcessor rpcReferenceBeanPostProcessor() {
        return new RpcReferenceBeanPostProcessor();
    }
}
