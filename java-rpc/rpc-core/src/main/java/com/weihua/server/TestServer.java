package com.weihua.server;

import common.service.UserService;
import common.service.impl.UserServiceImpl;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.impl.NettyRpcServer;

public class TestServer {
    public static void main(String[] args) {
        UserService userService = new UserServiceImpl();
        int port = 9999;
        String host = "127.0.0.1";

        ServiceProvider serviceProvider = new ServiceProvider(host, port);
        serviceProvider.provideServiceInterface(userService);

        RpcServer rpcServer=new NettyRpcServer(serviceProvider);
        rpcServer.start(port);

    }
}
