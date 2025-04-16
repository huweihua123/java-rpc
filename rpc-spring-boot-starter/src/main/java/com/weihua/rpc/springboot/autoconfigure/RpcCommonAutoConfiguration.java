/*
 * @Author: weihua hu
 * @Date: 2025-04-15 02:32:14
 * @LastEditTime: 2025-04-15 16:12:34
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.autoconfigure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.weihua.rpc.springboot.configurer.SerializerConfigurer;

/**
 * RPC公共自动配置类
 * 在所有模式下加载
 */
@Configuration
@Import({
        // RpcCommonConfiguration.class, // 导入核心公共配置
        SerializerConfigurer.class // 序列化配置绑定器
})
public class RpcCommonAutoConfiguration {
    // 公共配置,不需要额外Bean定义
}