/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:37:15
 * @LastEditTime: 2025-04-10 02:37:17
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.listener;

import com.weihua.rpc.core.server.RpcServer;
import com.weihua.rpc.core.server.config.ServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * RPC服务端启动监听器
 */
@Slf4j
public class RpcServerStartListener implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private RpcServer rpcServer;

    @Autowired
    private ServerConfig serverConfig;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保是根上下文才启动服务器
        if (event.getApplicationContext().getParent() == null) {
            if (!rpcServer.isRunning()) {
                try {
                    log.info("正在启动RPC服务器...");
                    rpcServer.start();
                    log.info("RPC服务器启动成功，监听地址: {}:{}",
                            serverConfig.getHost(), serverConfig.getPort());
                } catch (Exception e) {
                    log.error("RPC服务器启动失败", e);
                }
            } else {
                log.info("RPC服务器已经在运行中");
            }
        }
    }
}
