package com.weihua.rpc.example.hybrid.controller;

import com.weihua.rpc.example.common.api.HybridService;
import com.weihua.rpc.example.common.api.OrderService;
import com.weihua.rpc.example.common.api.UserService;
import com.weihua.rpc.example.common.model.Order;
import com.weihua.rpc.example.common.model.User;
import com.weihua.rpc.spring.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合模式测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/hybrid")
public class HybridController {

    @Autowired
    private HybridService localHybridService;  // 本地注入的服务实现
    
    @RpcReference
    private HybridService remoteHybridService; // RPC远程服务引用
    
    @RpcReference
    private UserService userService;
    
    @RpcReference
    private OrderService orderService;
    
    /**
     * 测试本地服务
     */
    @GetMapping("/local")
    public Map<String, Object> testLocalService() {
        log.info("测试本地混合服务");
        Map<String, Object> result = new HashMap<>();
        
        // 调用本地服务
        result.put("serviceInfo", localHybridService.getServiceInfo());
        result.put("users", localHybridService.processAllUsers());
        
        return result;
    }
    
    /**
     * 测试远程服务
     */
    @GetMapping("/remote")
    public Map<String, Object> testRemoteService() {
        log.info("测试远程混合服务");
        Map<String, Object> result = new HashMap<>();
        
        // 调用远程服务
        result.put("serviceInfo", remoteHybridService.getServiceInfo());
        result.put("users", remoteHybridService.processAllUsers());
        
        return result;
    }
    
    /**
     * 测试用户订单
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserWithOrders(@PathVariable("userId") Long userId) {
        log.info("获取用户及订单信息: userId={}", userId);
        Map<String, Object> result = new HashMap<>();
        
        // 获取用户信息
        User user = userService.getUserById(userId);
        result.put("user", user);
        
        if (user != null) {
            // 获取用户订单
            List<Order> orders = orderService.getOrdersByUserId(userId);
            result.put("orders", orders);
            
            // 调用混合服务计算订单数量
            int orderCount = localHybridService.countUserOrders(userId);
            result.put("orderCount", orderCount);
        }
        
        return result;
    }
    
    /**
     * 测试同时调用多个远程服务
     */
    @GetMapping("/test-all")
    public Map<String, Object> testAllServices() {
        log.info("测试所有远程服务");
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 调用用户服务
            result.put("allUsers", userService.getAllUsers());
            
            // 调用订单服务(用户ID=1)
            result.put("userOrders", orderService.getOrdersByUserId(1L));
            
            // 调用混合服务
            result.put("processedUsers", remoteHybridService.processAllUsers());
            result.put("hybridServiceInfo", remoteHybridService.getServiceInfo());
            
            result.put("success", true);
        } catch (Exception e) {
            log.error("测试服务异常", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
