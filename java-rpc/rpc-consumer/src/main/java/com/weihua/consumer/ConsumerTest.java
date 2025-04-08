/*
 * @Author: weihua hu
 * @Date: 2025-03-24 16:34:15
 * @LastEditTime: 2025-04-05 02:14:18
 * @LastEditors: weihua hu
 * @Description: RPC消费者测试程序
 */
package com.weihua.consumer;

import com.weihua.client.proxy.ClientProxy;
import com.weihua.pojo.User;
import com.weihua.service.UserService;
import common.bootstrap.RpcBootstrap;
import common.config.ConfigurationManager;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ConsumerTest {

    public static void main(String[] args) throws InterruptedException {
        // 初始化RPC消费者
        RpcBootstrap.initializeConsumer();

        // 获取配置
        ConfigurationManager configManager = ConfigurationManager.getInstance();

        // 从配置读取测试参数
        int threadPoolSize = configManager.getInt("rpc.consumer.threadpool.size", 20);
        int requestCount = configManager.getInt("rpc.consumer.test.request.count", 100);
        long testDelayMs = configManager.getLong("rpc.consumer.test.delay.ms", 1);
        int testBatchSize = configManager.getInt("rpc.consumer.test.batch.size", 100);

        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        log.info("使用线程池大小: {}", threadPoolSize);

        // 使用配置的注册中心类型
        String serviceCenterType = configManager.getString("rpc.registry", "consul");
        ClientProxy clientProxy = new ClientProxy(serviceCenterType);
        log.info("使用服务注册中心: {}", serviceCenterType);

        // 获取代理
        UserService proxy = clientProxy.getProxy(UserService.class);

        // 发送测试请求
        log.info("开始发送 {} 个测试请求...", requestCount);
        for (int i = 0; i < requestCount; i++) {
            final Integer i1 = i;
            if (i % testBatchSize == 0) {
                Thread.sleep(testDelayMs);
            }

            executorService.submit(() -> {
                try {
                    // 调用getUserByUserId方法
                    User user = proxy.getUserByUserId(i1);
                    if (user != null) {
                        log.info("从服务端得到的user={}", user);
                    } else {
                        log.warn("获取的 user 为 null, userId={}", i1);
                    }

                    // 调用insertUserId方法
                    Integer id = proxy.insertUserId(User.builder().id(i1).userName("User" + i1).sex(true).build());
                    if (id != null) {
                        log.info("向服务端插入user的id={}", id);
                    } else {
                        log.warn("插入失败，返回的id为null, userId={}", i1);
                    }
                } catch (Exception e) {
                    log.error("调用服务失败", e);
                }
            });
        }

        // 等待请求完成
        executorService.shutdown();
        executorService.awaitTermination(6000, TimeUnit.SECONDS);

        log.info("测试完成，正在关闭...");
        // clientProxy.close();
    }
}
