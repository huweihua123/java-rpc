/*
 * @Author: weihua hu
 * @Date: 2025-04-10 15:06:05
 * @LastEditTime: 2025-04-10 15:06:07
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.consumer.controller;

import com.weihua.rpc.example.common.api.OrderService;
import com.weihua.rpc.example.common.model.Order;
import com.weihua.rpc.spring.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 订单控制器
 * 演示如何通过@RpcReference注解引用远程服务，并设置重试次数和超时时间
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @RpcReference(retries = 3, timeout = 5000)
    private OrderService orderService;

    @GetMapping("/{orderId}")
    public Order getOrderById(@PathVariable("orderId") String orderId) {
        log.info("获取订单: orderId={}", orderId);
        return orderService.getOrderById(orderId);
    }

    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUserId(@PathVariable("userId") Long userId) {
        log.info("获取用户订单: userId={}", userId);
        return orderService.getOrdersByUserId(userId);
    }

    @PostMapping
    public String createOrder(@RequestBody Order order) {
        log.info("创建订单: {}", order);
        return orderService.createOrder(order);
    }

    @PostMapping("/{orderId}/pay")
    public boolean payOrder(@PathVariable("orderId") String orderId) {
        String paymentId = "PAY" + UUID.randomUUID().toString().substring(0, 8);
        log.info("支付订单: orderId={}, paymentId={}", orderId, paymentId);
        return orderService.payOrder(orderId, paymentId);
    }

    @PostMapping("/{orderId}/cancel")
    public boolean cancelOrder(@PathVariable("orderId") String orderId) {
        log.info("取消订单: orderId={}", orderId);
        return orderService.cancelOrder(orderId);
    }
}
