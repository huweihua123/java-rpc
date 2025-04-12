/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:00:05
 * @LastEditTime: 2025-04-10 23:45:04
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.core.client.registry;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 服务发现中心接口
 */
public interface ServiceCenter {

    /**
     * 发现指定服务的所有可用Invoker
     *
     * @param request RPC请求，包含服务名、版本等信息
     * @return 可用的Invoker列表
     */
    List<Invoker> discoverInvokers(RpcRequest request);

    /**
     * 发现服务地址 (向后兼容方法)
     *
     * @param request RPC请求
     * @return 服务地址
     * @deprecated 使用 {@link #discoverInvokers} 代替
     */
    @Deprecated
    InetSocketAddress serviceDiscovery(RpcRequest request);

    /**
     * 检查方法是否可重试
     *
     * @param methodSignature 方法签名，格式如：com.example.Service#methodName(paramTypes)
     * @return 如果方法可以重试，返回true
     */
    boolean isMethodRetryable(String methodSignature);

    /**
     * 获取服务元数据信息
     *
     * @param serviceName 服务名称
     * @return 元数据信息
     */
    Map<String, String> getServiceMetadata(String serviceName);

    /**
     * 订阅服务地址变更
     *
     * @param serviceName, 服务名称
     * @param listener     地址变更监听器
     */
    void subscribeAddressChange(String serviceName, Consumer<List<String>> listener);

    /**
     * 取消订阅服务地址变更
     *
     * @param serviceName 服务名称
     * @param listener    地址变更监听器
     */
    void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener);

    /**
     * 手动触发指定服务的同步
     * 
     * @param serviceName 服务名称
     * @return 同步成功返回true，否则返回false
     */
    boolean forceSync(String serviceName);

    /**
     * 获取服务健康状态
     * 
     * @param serviceName 服务名称
     * @return 如果服务健康返回true，否则返回false
     */
    boolean isServiceHealthy(String serviceName);

    /**
     * 关闭服务中心连接
     */
    void close();
}