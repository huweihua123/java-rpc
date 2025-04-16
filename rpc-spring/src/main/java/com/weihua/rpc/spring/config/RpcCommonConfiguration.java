/*
 * @Author: weihua hu
 * @Date: 2025-04-14 23:51:54
 * @LastEditTime: 2025-04-15 01:46:52
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.config;

import com.weihua.rpc.core.serialize.SerializerFactory;
import com.weihua.rpc.core.serialize.config.SerializerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RPC公共组件配置类
 * 无论是客户端还是服务端都需要的公共组件
 */
@Configuration
public class RpcCommonConfiguration {

    /**
     * 序列化器配置
     * 只有在没有自定义配置时才使用默认值
     */
    @Bean
    @ConditionalOnMissingBean
    public SerializerConfig serializerConfig() {
        return new SerializerConfig() {
            @Override
            public String getType() {
                return "json"; // 默认json序列化器
            }

            @Override
            public void initializeFactory() {
                SerializerFactory.initFromType(getType());
            }
        };
    }
}