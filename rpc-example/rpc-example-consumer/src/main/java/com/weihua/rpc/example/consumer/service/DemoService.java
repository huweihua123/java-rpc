package com.weihua.rpc.example.consumer.service;

import com.weihua.rpc.example.common.api.OrderService;
import com.weihua.rpc.example.common.api.UserService;
import com.weihua.rpc.example.common.model.Order;
import com.weihua.rpc.example.common.model.User;
import com.weihua.rpc.spring.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 演示服务
 * 实现CommandLineRunner接口，在应用启动后进行RPC调用测试
 */
@Slf4j
@Service
public class DemoService implements CommandLineRunner {

    @RpcReference
    private UserService userService;

    @RpcReference(retries = 3)
    private OrderService orderService;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始RPC调用测试...");

        // 测试用户服务
        testUserService();

        // 测试订单服务
        testOrderService();

        log.info("RPC调用测试完成!");
    }

    private void testUserService() {
        try {
            // 获取所有用户
            List<User> users = userService.getAllUsers();
            log.info("获取到的用户数量: {}", users.size());

            if (!users.isEmpty()) {
                // 获取第一个用户的详细信息
                User firstUser = users.get(0);
                User userDetail = userService.getUserById(firstUser.getId());
                log.info("获取到用户详情: {}", userDetail);

                // 更新用户信息
                userDetail.setEmail("updated_" + userDetail.getEmail());
                boolean updateResult = userService.updateUser(userDetail);
                log.info("更新用户结果: {}", updateResult);
            }

            // 创建新用户
            User newUser = User.builder()
                    .username("testuser")
                    .realName("测试用户")
                    .email("testuser@example.com")
                    .phone("13900000000")
                    .status(1)
                    .build();

            User createdUser = userService.createUser(newUser);
            log.info("创建用户成功: {}", createdUser);

        } catch (Exception e) {
            log.error("测试用户服务异常", e);
        }
    }

    private void testOrderService() {
        try {
            // 获取用户的订单
            List<Order> orders = orderService.getOrdersByUserId(1L);
            log.info("用户1的订单数量: {}", orders.size());

            if (!orders.isEmpty()) {
                // 获取第一个订单的详细信息
                Order firstOrder = orders.get(0);
                Order orderDetail = orderService.getOrderById(firstOrder.getId());
                log.info("获取到订单详情: {}", orderDetail);
            }

            // 创建新订单
            Order newOrder = Order.builder()
                    .userId(1L)
                    .amount(new BigDecimal("88.88"))
                    .payStatus(0)
                    .orderStatus(1)
                    .createTime(new Date())
                    .build();

            String orderId = orderService.createOrder(newOrder);
            log.info("创建订单成功: {}", orderId);

            // 支付订单
            boolean payResult = orderService.payOrder(orderId, "PAY123456");
            log.info("支付订单结果: {}", payResult);

            // 创建待取消的订单
            Order cancelOrder = Order.builder()
                    .userId(2L)
                    .amount(new BigDecimal("55.55"))
                    .payStatus(0)
                    .orderStatus(1)
                    .createTime(new Date())
                    .build();

            String cancelOrderId = orderService.createOrder(cancelOrder);
            log.info("创建待取消订单成功: {}", cancelOrderId);

            // 取消订单
            boolean cancelResult = orderService.cancelOrder(cancelOrderId);
            log.info("取消订单结果: {}", cancelResult);

        } catch (Exception e) {
            log.error("测试订单服务异常", e);
        }
    }
}
