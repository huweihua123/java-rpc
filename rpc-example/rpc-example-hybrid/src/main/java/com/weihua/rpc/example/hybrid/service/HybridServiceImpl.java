/*
 * @Author: weihua hu
 * @Date: 2025-04-12 13:53:22
 * @LastEditTime: 2025-04-12 13:53:24
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.hybrid.service;

import com.weihua.rpc.example.common.api.HybridService;
import com.weihua.rpc.example.common.api.OrderService;
import com.weihua.rpc.example.common.api.UserService;
import com.weihua.rpc.example.common.model.Order;
import com.weihua.rpc.example.common.model.User;
import com.weihua.rpc.spring.annotation.RpcReference;
import com.weihua.rpc.spring.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 混合服务实现类
 * 既是服务提供者又是服务消费者
 */
@Slf4j
@Service
@RpcService(interfaceClass = HybridService.class)
public class HybridServiceImpl implements HybridService {

    @RpcReference
    private UserService userService;

    @RpcReference
    private OrderService orderService;

    @Value("${server.port}")
    private int serverPort;

    @Override
    public List<User> processAllUsers() {
        log.info("处理所有用户数据");
        // 作为消费者调用UserService
        List<User> allUsers = userService.getAllUsers();
        
        // 对用户数据进行处理（示例：为每个用户添加订单数量）
        return allUsers.stream()
                .peek(user -> {
                    int orderCount = countUserOrders(user.getId());
                    user.setUsername(user.getUsername() + " (订单数: " + orderCount + ")");
                })
                .collect(Collectors.toList());
    }

    @Override
    public int countUserOrders(Long userId) {
        log.info("统计用户订单数量: userId={}", userId);
        // 作为消费者调用OrderService
        List<Order> userOrders = orderService.getOrdersByUserId(userId);
        return userOrders != null ? userOrders.size() : 0;
    }

    @Override
    public String getServiceInfo() {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            return String.format("混合服务节点信息 - 主机: %s, 端口: %d, 时间戳: %d",
                    hostAddress, serverPort, System.currentTimeMillis());
        } catch (UnknownHostException e) {
            log.error("获取主机地址失败", e);
            return "未知节点 (端口: " + serverPort + ")";
        }
    }
}
