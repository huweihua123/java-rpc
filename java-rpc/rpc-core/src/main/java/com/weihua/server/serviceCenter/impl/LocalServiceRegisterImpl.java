/*
 * @Author: weihua hu
 * @Date: 2025-04-04 15:54:46
 * @LastEditTime: 2025-04-04 20:35:55
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.server.serviceCenter.impl;

import com.weihua.server.serviceCenter.ServiceRegister;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地服务注册实现，用于开发测试环境
 * 不依赖外部注册中心，在单机内存中注册服务
 */
@Log4j2
public class LocalServiceRegisterImpl implements ServiceRegister {

    // 服务映射: 服务名 -> 地址列表
    private final Map<String, Map<String, InetSocketAddress>> services = new ConcurrentHashMap<>();

    @Override
    public void register(Class<?> serviceClass, InetSocketAddress serviceAddress) {
        String serviceName = serviceClass.getName();
        String addressKey = serviceAddress.getHostName() + ":" + serviceAddress.getPort();

        services.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>())
                .put(addressKey, serviceAddress);

        log.info("本地注册服务: {}, 地址: {}:{}",
                serviceName, serviceAddress.getHostName(), serviceAddress.getPort());
    }

    /**
     * 查询服务地址
     * 
     * @param serviceName 服务名称
     * @return 服务地址
     */
    public InetSocketAddress lookup(String serviceName) {
        Map<String, InetSocketAddress> addresses = services.get(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            log.warn("服务不存在: {}", serviceName);
            return null;
        }

        // 简单返回第一个地址
        return addresses.values().iterator().next();
    }

    /**
     * 获取所有已注册的服务列表
     */
    public Map<String, Map<String, InetSocketAddress>> getAllServices() {
        return new HashMap<>(services);
    }

    @Override
    public String toString() {
        return "local";
    }
}
/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-04 15:54:46
 * 
 * @LastEditTime: 2025-04-04 15:54:46
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
