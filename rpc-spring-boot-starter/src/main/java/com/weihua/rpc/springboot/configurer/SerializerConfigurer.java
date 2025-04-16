/*
 * @Author: weihua hu
 * @Date: 2025-04-15 03:48:21
 * @LastEditTime: 2025-04-15 02:11:52
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.configurer;

import com.weihua.rpc.core.serialize.SerializerFactory;
import com.weihua.rpc.core.serialize.config.SerializerConfig;
import com.weihua.rpc.springboot.properties.SerializeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 序列化器配置绑定器
 * 负责将配置属性绑定到序列化器配置对象
 */
@Configuration
@EnableConfigurationProperties(SerializeProperties.class)
public class SerializerConfigurer {

    /**
     * 创建序列化器配置对象
     * 
     * @param properties 序列化配置属性
     * @return 序列化器配置对象
     */
    @Bean
    public SerializerConfig serializerConfig(SerializeProperties properties) {
        return new SerializerConfig() {
            @Override
            public String getType() {
                return properties.getType();
            }

            @Override
            public void initializeFactory() {
                // 根据配置初始化序列化工厂
                SerializerFactory.initFromType(getType());
            }
        };
    }
}