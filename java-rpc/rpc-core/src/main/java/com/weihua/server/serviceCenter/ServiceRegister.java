package com.weihua.server.serviceCenter;

import common.spi.annotation.SPI;

import java.net.InetSocketAddress;

@SPI("consul")
public interface ServiceRegister {
    /**
     * 注册服务
     * 
     * @param clazz             服务类
     * @param inetSocketAddress 服务地址
     */
    void register(Class<?> clazz, InetSocketAddress inetSocketAddress);

    /**
     * 关闭注册中心连接
     */
    default void shutdown() {
        // 默认空实现
    }

}