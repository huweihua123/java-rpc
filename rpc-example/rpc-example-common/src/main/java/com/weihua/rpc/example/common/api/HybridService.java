/*
 * @Author: weihua hu
 * @Date: 2025-04-12 13:52:31
 * @LastEditTime: 2025-04-12 13:52:33
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.common.api;

import com.weihua.rpc.example.common.model.User;

import java.util.List;

/**
 * 混合服务接口
 * 用于测试同时作为服务提供者和消费者的场景
 */
public interface HybridService {

    /**
     * 获取所有用户并处理
     *
     * @return 处理后的用户列表
     */
    List<User> processAllUsers();

    /**
     * 查询用户订单数量
     *
     * @param userId 用户ID
     * @return 用户订单数量
     */
    int countUserOrders(Long userId);

    /**
     * 获取服务节点信息
     *
     * @return 服务节点信息
     */
    String getServiceInfo();
}
