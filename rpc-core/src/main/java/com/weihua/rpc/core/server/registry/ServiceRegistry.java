/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:18:30
 * @LastEditTime: 2025-04-10 02:18:31
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.registry;


import com.weihua.rpc.common.extension.SPI;

import java.net.InetSocketAddress;

/**
 * 服务注册中心接口
 * 负责将服务注册到注册中心
 */
@SPI("consul")
public interface ServiceRegistry {

    /**
     * 注册服务
     * 
     * @param clazz          服务接口类
     * @param serviceAddress 服务地址
     */
    void register(Class<?> clazz, InetSocketAddress serviceAddress);

    /**
     * 关闭注册中心连接并释放资源
     */
    void shutdown();
}
