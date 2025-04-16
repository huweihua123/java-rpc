/*
 * @Author: weihua hu
 * @Date: 2025-04-15 00:25:39
 * @LastEditTime: 2025-04-15 00:25:41
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.registry;

import com.weihua.rpc.core.server.config.RegistryConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * 注册中心抽象适配器
 * 提供配置管理和生命周期管理
 */
@Slf4j
public abstract class AbstractServiceRegistry implements ServiceRegistry {

    protected RegistryConfig registryConfig;

    public void setRegistryConfig(RegistryConfig config) {
        this.registryConfig = config;
    }

    /**
     * 初始化连接
     */
    public abstract void init();

    /**
     * 刷新配置
     */
    public void refreshConfig() {
        log.info("刷新配置: {}", this.getClass().getSimpleName());
        try {
            shutdown();
            init();
        } catch (Exception e) {
            log.error("刷新配置失败", e);
        }
    }
}