/*
 * @Author: weihua hu
 * @Date: 2025-04-14 16:16:38
 * @LastEditTime: 2025-04-14 16:16:40
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.serialize.config;

/**
 * 序列化器配置接口
 */
public interface SerializerConfig {
    /**
     * 获取序列化类型
     */
    String getType();

    /**
     * 初始化序列化器工厂
     */
    default void initializeFactory() {
        // 默认空实现，子类可覆盖
    }
}