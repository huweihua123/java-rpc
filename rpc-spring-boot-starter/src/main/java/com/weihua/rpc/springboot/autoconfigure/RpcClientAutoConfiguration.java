/*
 * @Author: weihua hu
 * @Date: 2025-04-15 02:30:45
 * @LastEditTime: 2025-04-15 02:08:24
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.autoconfigure;

import com.weihua.rpc.core.condition.ConditionalOnClientMode;
import com.weihua.rpc.spring.config.RpcClientConfiguration;
import com.weihua.rpc.spring.processor.RpcReferenceBeanPostProcessor;
import com.weihua.rpc.springboot.configurer.CircuitBreakerConfigurer;
import com.weihua.rpc.springboot.configurer.ClientConfigurer;
import com.weihua.rpc.springboot.configurer.DiscoveryConfigurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC客户端自动配置类
 * 仅在客户端模式下加载
 */
@Configuration
@ConditionalOnClientMode
@Import({
        RpcClientConfiguration.class, // 导入核心客户端配置
        ClientConfigurer.class, // 客户端配置绑定器
        DiscoveryConfigurer.class, // 服务发现配置绑定器
        CircuitBreakerConfigurer.class // 添加熔断器配置绑定器
})
public class RpcClientAutoConfiguration {

    /**
     * 注册RPC引用注解处理器
     */
    @Bean
    public RpcReferenceBeanPostProcessor rpcReferenceBeanPostProcessor() {
        return new RpcReferenceBeanPostProcessor();
    }
}