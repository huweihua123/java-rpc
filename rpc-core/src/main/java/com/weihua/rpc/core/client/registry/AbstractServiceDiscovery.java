/*
 * @Author: weihua hu
 * @Date: 2025-04-15 00:39:59
 * @LastEditTime: 2025-04-15 00:40:01
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry;

import com.weihua.rpc.core.client.cache.ServiceAddressCache;
import com.weihua.rpc.core.client.config.DiscoveryConfig;
import com.weihua.rpc.core.client.pool.InvokerManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务发现抽象适配器
 * 提供配置管理和生命周期管理
 */
@Slf4j
public abstract class AbstractServiceDiscovery implements ServiceDiscovery {

    protected DiscoveryConfig discoveryConfig;
    protected ServiceAddressCache addressCache;
    protected InvokerManager invokerManager;

    /**
     * 设置服务发现配置
     */
    public void setDiscoveryConfig(DiscoveryConfig config) {
        this.discoveryConfig = config;
    }

    /**
     * 设置地址缓存组件
     */
    public void setAddressCache(ServiceAddressCache addressCache) {
        this.addressCache = addressCache;
    }

    /**
     * 设置Invoker管理器
     */
    public void setInvokerManager(InvokerManager invokerManager) {
        this.invokerManager = invokerManager;
    }

    /**
     * 初始化服务发现
     * 由工厂在所有依赖注入完成后调用
     */
    public abstract void init();

    /**
     * 刷新配置
     */
    public void refreshConfig() {
        log.info("刷新{}配置", this.getClass().getSimpleName());
        try {
            close();
            init();
        } catch (Exception e) {
            log.error("刷新配置失败", e);
        }
    }
}