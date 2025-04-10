/*
 * @Author: weihua hu
 * @Date: 2025-04-10 23:21:01
 * @LastEditTime: 2025-04-10 23:21:05
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.cache;

import java.util.List;
import java.util.function.Consumer;

/**
 * 服务地址缓存接口
 * 负责缓存服务地址并提供订阅机制
 */
public interface ServiceAddressCache {
    
    /**
     * 获取服务地址列表
     * @param serviceName 服务名称
     * @return 地址列表
     */
    List<String> getAddresses(String serviceName);
    
    /**
     * 更新服务地址列表
     * @param serviceName 服务名称
     * @param addresses 地址列表
     */
    void updateAddresses(String serviceName, List<String> addresses);
    
    /**
     * 订阅地址变更
     * @param serviceName 服务名称
     * @param listener 变更监听器
     */
    void subscribeAddressChange(String serviceName, Consumer<List<String>> listener);
    
    /**
     * 取消订阅地址变更
     * @param serviceName 服务名称
     * @param listener 变更监听器
     */
    void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener);
    
    /**
     * 服务是否可用（有地址）
     * @param serviceName 服务名称
     * @return 是否可用
     */
    boolean isServiceAvailable(String serviceName);
    
    /**
     * 添加服务下线监听器
     * @param serviceName 服务名称
     * @param listener 下线监听器
     */
    void addServiceUnavailableListener(String serviceName, Runnable listener);
    
    /**
     * 清理缓存资源
     */
    void close();
}