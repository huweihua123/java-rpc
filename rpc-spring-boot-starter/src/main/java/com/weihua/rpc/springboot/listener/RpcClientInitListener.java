/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:37:09
 * @LastEditTime: 2025-04-12 14:04:08
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.listener;

import com.weihua.rpc.core.client.circuit.CircuitBreakerProvider;
import com.weihua.rpc.core.client.netty.NettyRpcClient;
import com.weihua.rpc.core.client.registry.ServiceCenter;
import com.weihua.rpc.core.condition.ConditionalOnClientMode;
import com.weihua.rpc.core.condition.ConditionalOnServerMode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * RPC客户端初始化监听器
 */
@Slf4j
// @ConditionalOnProperty(name = "rpc.mode", havingValue = "client",
// matchIfMissing = false)
@ConditionalOnClientMode
public class RpcClientInitListener implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private NettyRpcClient rpcClient;

    @Autowired
    private ServiceCenter serviceCenter;

    @Autowired
    private CircuitBreakerProvider circuitBreakerProvider;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保是根上下文才初始化
        if (event.getApplicationContext().getParent() == null) {
            log.info("RPC客户端初始化开始");

            try {
                // 这里客户端的初始化工作在@PostConstruct中已完成
                // 这里只做一些额外的检查和日志记录

                log.info("RPC客户端初始化成功，使用注册中心: {}", serviceCenter);

                // 输出一些关键配置信息
                log.info("熔断器已配置，默认状态: {}",
                        circuitBreakerProvider != null ? "启用" : "禁用");

            } catch (Exception e) {
                log.error("RPC客户端初始化失败", e);
            }
        }
    }
}
