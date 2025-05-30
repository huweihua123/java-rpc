/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:20:25
 * @LastEditTime: 2025-04-15 00:35:15
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.registry.impl;

import com.weihua.rpc.core.server.registry.AbstractServiceRegistry;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地服务注册实现
 * 用于开发和测试环境，不依赖外部注册中心
 */
@Slf4j
public class LocalServiceRegistry extends AbstractServiceRegistry {

    // 服务注册表，存储服务名称到地址的映射
    private final Map<String, InetSocketAddress> serviceRegistry = new ConcurrentHashMap<>();

    @Override
    public void init() {
        log.info("初始化本地服务注册中心");
        // 本地注册中心不需要额外初始化
    }

    @Override
    public void register(Class<?> clazz, InetSocketAddress serviceAddress) {
        String serviceName = clazz.getName();
        serviceRegistry.put(serviceName, serviceAddress);
        log.info("本地注册服务: {} -> {}:{}",
                serviceName, serviceAddress.getHostString(), serviceAddress.getPort());
    }

    @Override
    public void shutdown() {
        serviceRegistry.clear();
        log.info("本地服务注册表已清空");
    }

    /**
     * 查询服务地址
     * 
     * @param serviceName 服务名称
     * @return 服务地址，如果未找到返回null
     */
    public InetSocketAddress lookup(String serviceName) {
        InetSocketAddress address = serviceRegistry.get(serviceName);
        if (address != null) {
            log.debug("本地查询服务: {} -> {}:{}",
                    serviceName, address.getHostString(), address.getPort());
        } else {
            log.warn("本地查询服务未找到: {}", serviceName);
        }
        return address;
    }

    /**
     * 获取注册的服务数量
     */
    public int getServiceCount() {
        return serviceRegistry.size();
    }

    @Override
    public String toString() {
        return "Local";
    }
}