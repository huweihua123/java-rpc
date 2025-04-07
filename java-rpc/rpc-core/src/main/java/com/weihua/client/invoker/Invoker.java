/*
 * @Author: weihua hu
 * @Date: 2025-04-06 18:10:17
 * @LastEditTime: 2025-04-06 18:56:25
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.invoker;

import common.message.RpcRequest;
import common.message.RpcResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * Invoker接口，封装远程调用能力
 */
public interface Invoker {

    /**
     * 发送RPC请求并返回CompletableFuture
     * 
     * @param request RPC请求对象
     * @return 包含RPC响应的CompletableFuture
     */
    CompletableFuture<RpcResponse> invoke(RpcRequest request);

    /**
     * 获取当前活跃请求数
     */
    int getActiveCount();

    /**
     * 获取Invoker关联的地址
     */
    InetSocketAddress getAddress();

    /**
     * 检查Invoker是否可用
     */
    boolean isAvailable();

    /**
     * 获取Invoker的唯一标识
     */
    String getId();

    /**
     * 销毁Invoker，释放相关资源
     */
    void destroy();

    /**
     * 获取平均响应时间（毫秒）
     */
    double getAvgResponseTime();

    /**
     * 获取成功请求百分比
     */
    double getSuccessRate();
}