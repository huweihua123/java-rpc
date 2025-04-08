/*
 * @Author: weihua hu
 * @Date: 2025-03-21 23:41:56
 * @LastEditTime: 2025-04-06 20:03:33
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.serverCenter;

import com.weihua.client.invoker.Invoker;
import common.message.RpcRequest;
import common.spi.annotation.SPI;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Consumer;

@SPI("consul") // 默认使用Consul
public interface ServiceCenter {

    /**
     * 服务发现 - 获取服务地址
     * 
     * @param rpcRequest RPC请求对象
     * @return 服务地址
     */
    InetSocketAddress serviceDiscovery(RpcRequest rpcRequest);

    /**
     * 服务发现 - 获取可用的Invoker列表
     * 
     * @param rpcRequest RPC请求对象
     * @return Invoker列表
     */
    default List<Invoker> discoverInvokers(RpcRequest rpcRequest) {
        throw new UnsupportedOperationException("此服务中心不支持基于Invoker的服务发现");
    }

    /**
     * 检查方法是否支持重试
     * 
     * @param serviceAddress  服务地址
     * @param methodSignature 方法签名
     * @return 是否支持重试
     */
    boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature);

    /**
     * 订阅服务地址变更事件
     * 
     * @param serviceName 服务名称
     * @param listener    地址变更监听器
     */
    default void subscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        // 默认实现为空，子类可以重写此方法提供实际功能
    }

    /**
     * 取消订阅服务地址变更事件
     * 
     * @param serviceName 服务名称
     * @param listener    地址变更监听器
     */
    default void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        // 默认实现为空，子类可以重写此方法提供实际功能
    }

    /**
     * 关闭服务中心
     */
    void close();
}