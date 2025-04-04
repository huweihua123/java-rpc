/*
 * @Author: weihua hu
 * @Date: 2025-03-24 14:43:20
 * @LastEditTime: 2025-04-04 15:44:58
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.server;

import com.weihua.server.provider.ServiceProvider;
import com.weihua.server.server.RpcServer;
import com.weihua.server.server.impl.NettyRpcServer;
import com.weihua.service.UserService;
import com.weihua.service.impl.UserServiceImpl;
import common.bootstrap.RpcBootstrap;
import common.config.ConfigurationManager;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TestServer {
    public static void main(String[] args) {
        // 初始化RPC提供者
        RpcBootstrap.initializeProvider();

        // 获取配置
        ConfigurationManager configManager = ConfigurationManager.getInstance();

        // 获取服务主机和端口
        String host = configManager.getString("rpc.provider.host", "127.0.0.1");
        if ("auto".equals(host)) {
            // 自动获取本机IP
            host = getLocalIp();
        }

        int port = configManager.getInt("rpc.provider.port", 9999);
        log.info("RPC服务器将在 {}:{} 启动", host, port);

        // 创建服务实例
        UserService userService = new UserServiceImpl();

        // 创建服务提供者
        ServiceProvider serviceProvider = new ServiceProvider(host, port);

        // 注册服务接口
        serviceProvider.provideServiceInterface(userService);
        log.info("注册服务: {}", UserService.class.getName());

        // 创建并启动RPC服务器
        RpcServer rpcServer = new NettyRpcServer(serviceProvider);
        log.info("RPC服务器启动中...");
        rpcServer.start(port);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("接收到关闭信号，正在关闭RPC服务器...");
            rpcServer.stop();
            log.info("RPC服务器已关闭");
        }));
    }

    // 获取本机IP地址
    private static String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("无法获取本机IP地址，使用127.0.0.1", e);
            return "127.0.0.1";
        }
    }
}
