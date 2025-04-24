/*
 * @Author: weihua hu
 * @Date: 2025-04-10 23:21:01
 * @LastEditTime: 2025-04-10 23:28:41
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.cache;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 服务地址缓存接口
 */
public interface ServiceAddressCache {

    /**
     * 获取服务地址列表
     *
     * @param serviceName 服务名称
     * @return 地址列表
     */
    List<String> getAddresses(String serviceName);

    /**
     * 更新服务地址列表
     *
     * @param serviceName 服务名称
     * @param addresses   地址列表
     */
    void updateAddresses(String serviceName, List<String> addresses);

    /**
     * 订阅服务地址变更
     *
     * @param serviceName 服务名称
     * @param listener    变更监听器
     */
    void subscribeAddressChange(String serviceName, Consumer<List<String>> listener);

    /**
     * 取消订阅服务地址变更
     *
     * @param serviceName 服务名称
     * @param listener    变更监听器
     */
    void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener);

    /**
     * 获取所有已缓存的服务名称
     * 
     * @return 服务名称集合
     */
    Set<String> getAllServiceNames();

    /**
     * 关闭缓存，释放资源
     */
    void close();
}