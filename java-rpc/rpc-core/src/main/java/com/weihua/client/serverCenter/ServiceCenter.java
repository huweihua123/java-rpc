/*
 * @Author: weihua hu
 * @Date: 2025-03-21 23:41:56
 * @LastEditTime: 2025-04-04 15:13:49
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.serverCenter;

import common.message.RpcRequest;
import common.spi.annotation.SPI;

import java.net.InetSocketAddress;

@SPI("consul") // 默认使用Consul
public interface ServiceCenter {

    InetSocketAddress serviceDiscovery(RpcRequest rpcRequest);

    boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature);

    void close();
}