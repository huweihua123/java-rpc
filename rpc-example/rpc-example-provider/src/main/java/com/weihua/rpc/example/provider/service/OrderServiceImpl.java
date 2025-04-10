package com.weihua.rpc.example.provider.service;

import com.weihua.rpc.example.common.api.OrderService;
import com.weihua.rpc.example.common.model.Order;
import com.weihua.rpc.spring.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 * 方法上加上retryable=true标记，表明该方法是幂等的，可以安全重试
 */
@Slf4j
@Service
@RpcService(interfaceClass = OrderService.class, retryable = true)
public class OrderServiceImpl implements OrderService {

    // 模拟订单存储
    private final Map<String, Order> orderMap = new ConcurrentHashMap<>();

    // 支付ID记录，用于确保支付幂等性
    private final Set<String> processedPaymentIds = ConcurrentHashMap.newKeySet();

    // 已取消订单记录
    private final Set<String> cancelledOrders = ConcurrentHashMap.newKeySet();

    public OrderServiceImpl() {
        // 初始化测试订单数据
        initTestOrders();
    }

    private void initTestOrders() {
        // 为用户ID=1创建两个订单
        createOrder(Order.builder()
                .id(generateOrderId())
                .userId(1L)
                .amount(new java.math.BigDecimal("100.50"))
                .payStatus(1)
                .orderStatus(2)
                .paymentId("PAY" + UUID.randomUUID().toString().substring(0, 8))
                .createTime(new Date())
                .payTime(new Date())
                .build());

        createOrder(Order.builder()
                .id(generateOrderId())
                .userId(1L)
                .amount(new java.math.BigDecimal("200.75"))
                .payStatus(0)
                .orderStatus(1)
                .createTime(new Date())
                .build());

        // 为用户ID=2创建一个订单
        createOrder(Order.builder()
                .id(generateOrderId())
                .userId(2L)
                .amount(new java.math.BigDecimal("99.99"))
                .payStatus(1)
                .orderStatus(3)
                .paymentId("PAY" + UUID.randomUUID().toString().substring(0, 8))
                .createTime(new Date())
                .payTime(new Date())
                .build());
    }

    /**
     * 生成订单ID
     */
    private String generateOrderId() {
        return "ORD" + System.currentTimeMillis() +
                UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public Order getOrderById(String orderId) {
        log.info("获取订单: orderId={}", orderId);
        return orderMap.get(orderId);
    }

    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        log.info("获取用户订单: userId={}", userId);
        return orderMap.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public String createOrder(Order order) {
        // 确保幂等性：如果订单ID已存在，直接返回
        if (order.getId() != null && orderMap.containsKey(order.getId())) {
            log.info("创建订单(幂等): 订单已存在, orderId={}", order.getId());
            return order.getId();
        }

        // 生成新的订单ID
        if (order.getId() == null) {
            order.setId(generateOrderId());
        }

        // 补充订单信息
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());
        if (order.getOrderStatus() == null) {
            order.setOrderStatus(1); // 待支付
        }
        if (order.getPayStatus() == null) {
            order.setPayStatus(0); // 未支付
        }

        // 保存订单
        orderMap.put(order.getId(), order);
        log.info("创建订单成功: {}", order);

        return order.getId();
    }

    @Override
    public boolean payOrder(String orderId, String paymentId) {
        // 确保幂等性：如果支付ID已处理过，直接返回成功
        if (processedPaymentIds.contains(paymentId)) {
            log.info("支付订单(幂等): 支付已处理, orderId={}, paymentId={}", orderId, paymentId);
            return true;
        }

        // 检查订单
        Order order = orderMap.get(orderId);
        if (order == null) {
            log.warn("支付订单失败: 订单不存在, orderId={}", orderId);
            return false;
        }

        // 检查订单状态
        if (order.getOrderStatus() == 0) {
            log.warn("支付订单失败: 订单已取消, orderId={}", orderId);
            return false;
        }

        // 检查支付状态
        if (order.getPayStatus() == 1) {
            log.info("支付订单(幂等): 订单已支付, orderId={}", orderId);
            processedPaymentIds.add(paymentId); // 记录支付ID
            return true;
        }

        // 更新订单
        order.setPayStatus(1); // 已支付
        order.setOrderStatus(2); // 已支付待发货
        order.setPaymentId(paymentId);
        order.setPayTime(new Date());
        order.setUpdateTime(new Date());

        // 记录支付ID，确保幂等性
        processedPaymentIds.add(paymentId);

        log.info("支付订单成功: orderId={}, paymentId={}", orderId, paymentId);
        return true;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        // 确保幂等性：如果订单已取消，直接返回成功
        if (cancelledOrders.contains(orderId)) {
            log.info("取消订单(幂等): 订单已取消, orderId={}", orderId);
            return true;
        }

        // 检查订单
        Order order = orderMap.get(orderId);
        if (order == null) {
            log.warn("取消订单失败: 订单不存在, orderId={}", orderId);
            return false;
        }

        // 检查订单状态：已支付的订单不能取消
        if (order.getPayStatus() == 1) {
            log.warn("取消订单失败: 订单已支付无法取消, orderId={}", orderId);
            return false;
        }

        // 更新订单状态
        order.setOrderStatus(0); // 已取消
        order.setUpdateTime(new Date());

        // 记录已取消订单，确保幂等性
        cancelledOrders.add(orderId);

        log.info("取消订单成功: orderId={}", orderId);
        return true;
    }
}
