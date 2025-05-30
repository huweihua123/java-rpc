/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:01:27
 * @LastEditTime: 2025-04-15 00:09:26
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry.balance;

import com.weihua.rpc.common.extension.SPI;
import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;

import java.util.List;

/**
 * 负载均衡接口
 */
@SPI("random") // 添加SPI注解，指定默认值为random
public interface LoadBalance {

    /**
     * 从可用的调用者列表中选择一个
     *
     * @param invokers 可用的调用者列表
     * @param request  RPC请求
     * @return 选中的调用者
     */
    Invoker select(List<Invoker> invokers, RpcRequest request);
}
