/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:32:46
 * @LastEditTime: 2025-04-10 02:32:50
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * RPC客户端配置类
 * 自动导入客户端所需的所有Bean
 */
@Configuration
@ComponentScan({
        "com.weihua.rpc.core.client",
        "com.weihua.rpc.core.protocol",
        "com.weihua.rpc.core.serialize"
})
public class RpcClientConfiguration {

    /**
     * 客户端配置初始化完成后的日志
     */
    public RpcClientConfiguration() {
//        System.out.println("RPC客户端配置已初始化");
    }
}
