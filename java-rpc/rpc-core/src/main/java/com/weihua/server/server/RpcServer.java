package com.weihua.server.server;

public interface RpcServer {
    void start(int port);
    void stop();
}
