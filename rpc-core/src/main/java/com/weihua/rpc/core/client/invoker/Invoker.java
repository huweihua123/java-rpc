/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:51:50
 * @LastEditTime: 2025-04-23 19:52:40
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.invoker;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * 调用者接口
 * 抽象远程调用的执行者
 */
public interface Invoker {

    /**
     * 异步调用请求
     *
     * @param request RPC请求对象
     * @return 包含RPC响应的Future对象
     */
    CompletableFuture<RpcResponse> invoke(RpcRequest request);

    /**
     * 获取调用者的地址
     *
     * @return 服务地址
     */
    InetSocketAddress getAddress();

    /**
     * 获取调用者ID
     *
     * @return 调用者ID
     */
    String getId();

    /**
     * 检查调用者是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取当前活跃请求数
     *
     * @return 活跃请求数
     */
    int getActiveCount();

    /**
     * 获取平均响应时间
     *
     * @return 平均响应时间(毫秒)
     */
    double getAvgResponseTime();

    /**
     * 获取成功率
     *
     * @return 成功率(0-1)
     */
    double getSuccessRate();

    /**
     * 获取总请求数量
     * 
     * @return 请求总数
     */
    long getRequestCount();

    /**
     * 销毁调用者，释放资源
     */
    void destroy();
}
