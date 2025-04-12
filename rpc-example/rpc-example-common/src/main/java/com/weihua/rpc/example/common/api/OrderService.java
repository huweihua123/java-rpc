/*
 * @Author: weihua hu
 * @Date: 2025-04-10 15:03:44
 * @LastEditTime: 2025-04-12 17:09:59
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.common.api;

import com.weihua.rpc.core.server.annotation.RateLimit;
import com.weihua.rpc.core.server.annotation.Retryable;
import com.weihua.rpc.example.common.model.Order;

import java.util.List;

/**
 * 订单服务接口
 * 演示幂等操作的服务接口，适合开启重试
 */
public interface OrderService {

    /**
     * 根据ID获取订单
     * 
     * @param orderId 订单ID
     * @return 订单信息
     */

    Order getOrderById(String orderId);

    /**
     * 根据用户ID获取订单列表
     * 
     * @param userId 用户ID
     * @return 订单列表
     */
    List<Order> getOrdersByUserId(Long userId);

    /**
     * 创建订单（幂等操作，相同订单号只会创建一次）
     * 
     * @param order 订单信息
     * @return 订单ID
     */
    String createOrder(Order order);

    /**
     * 支付订单（幂等操作，相同支付ID只会处理一次）
     * 
     * @param orderId   订单ID
     * @param paymentId 支付ID
     * @return 支付结果
     */
    boolean payOrder(String orderId, String paymentId);

    /**
     * 取消订单（幂等操作）
     * 
     * @param orderId 订单ID
     * @return 是否取消成功
     */
    boolean cancelOrder(String orderId);
}
