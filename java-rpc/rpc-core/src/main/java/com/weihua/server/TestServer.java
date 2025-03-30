package com.weihua.server;

import com.weihua.server.provider.ServiceProvider;
import com.weihua.server.server.RpcServer;
import com.weihua.server.server.impl.NettyRpcServer;
import com.weihua.service.UserService;
import com.weihua.service.impl.UserServiceImpl;

public class TestServer {
    public static void main(String[] args) {
        UserService userService = new UserServiceImpl();
        int port = 9999;
        String host = "127.0.0.1";

        ServiceProvider serviceProvider = new ServiceProvider(host, port);
        serviceProvider.provideServiceInterface(userService);

        RpcServer rpcServer = new NettyRpcServer(serviceProvider);
        rpcServer.start(port);

    }
}
