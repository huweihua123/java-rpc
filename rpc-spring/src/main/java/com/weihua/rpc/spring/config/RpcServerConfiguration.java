/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:33:00
 * @LastEditTime: 2025-04-10 02:33:02
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.spring.config;

import com.weihua.rpc.core.server.RpcServer;
import com.weihua.rpc.core.server.config.ServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import javax.annotation.PreDestroy;

/**
 * RPC服务端配置类
 * 自动导入服务端所需的所有Bean并处理服务器的启动和关闭
 */
@Configuration
@ComponentScan({
        "com.weihua.rpc.core.server",
        "com.weihua.rpc.core.protocol",
        "com.weihua.rpc.core.serialize"
})
@ConditionalOnProperty(name = "rpc.mode", havingValue = "server", matchIfMissing = false)
public class RpcServerConfiguration {

    @Autowired
    private RpcServer rpcServer;

    @Autowired
    private ServerConfig serverConfig;

    /**
     * 服务端配置初始化完成后的日志
     */
    public RpcServerConfiguration() {
    }

    /**
     * 当Spring上下文刷新完成后启动RPC服务器
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保是根上下文才启动服务器
        if (event.getApplicationContext().getParent() == null) {
            try {
                // 启动RPC服务器
                if (!rpcServer.isRunning()) {
                    rpcServer.start();
                    System.out.println("RPC服务器已启动，监听地址: " + serverConfig.getHost() + ":" + serverConfig.getPort());
                }
            } catch (Exception e) {
                System.err.println("RPC服务器启动失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 关闭RPC服务器
     */
    @PreDestroy
    public void stopRpcServer() {
        if (rpcServer != null && rpcServer.isRunning()) {
            rpcServer.stop();
            System.out.println("RPC服务器已关闭");
        }
    }
}
