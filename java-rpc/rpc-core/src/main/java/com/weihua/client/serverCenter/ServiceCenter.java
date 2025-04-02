package com.weihua.client.serverCenter;

import common.message.RpcRequest;

import java.net.InetSocketAddress;

public interface ServiceCenter {
//    InetSocketAddress serviceDiscovery(String serviceName);

    InetSocketAddress serviceDiscovery(RpcRequest rpcRequest);


    boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature);


    void close();
}