/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:33:24
 * @LastEditTime: 2025-04-15 01:46:39
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC自动配置类
 * 根据不同模式加载对应配置
 */
@Configuration
@Import(RpcCommonAutoConfiguration.class) // 无条件导入公共配置
public class RpcAutoConfiguration {

    /**
     * 服务端模式配置
     * 当mode=server时加载服务端配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "rpc", name = "mode", havingValue = "server", matchIfMissing = false)
    @Import(RpcServerAutoConfiguration.class)
    public static class ServerModeConfig {
        // 不需要额外的Bean定义
    }

    /**
     * 客户端模式配置
     * 当mode=client时加载客户端配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "rpc", name = "mode", havingValue = "client", matchIfMissing = false)
    @Import(RpcClientAutoConfiguration.class)
    public static class ClientModeConfig {
        // 不需要额外的Bean定义
    }

    /**
     * 混合模式配置
     * 当mode=mixed时同时加载客户端和服务端配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "rpc", name = "mode", havingValue = "mixed", matchIfMissing = false)
    @Import({
            RpcClientAutoConfiguration.class,
            RpcServerAutoConfiguration.class
    })
    public static class MixedModeConfig {
        // 不需要额外的Bean定义，所需Bean已在各自配置类中定义
    }
}