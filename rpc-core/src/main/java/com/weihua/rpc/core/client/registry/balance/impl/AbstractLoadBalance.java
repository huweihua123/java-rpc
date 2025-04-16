/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:01:44
 * @LastEditTime: 2025-04-15 00:36:32
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry.balance.impl;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;
import com.weihua.rpc.core.client.registry.balance.LoadBalance;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 负载均衡抽象类
 */
@Slf4j
public abstract class AbstractLoadBalance implements LoadBalance {

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        // 快速判断
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }

        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        // 执行具体的负载均衡算法
        return doSelect(invokers, request);
    }

    /**
     * 子类实现具体的负载均衡算法
     * 
     * @param invokers 可用的调用者列表
     * @param request  RPC请求
     * @return 选中的调用者
     */
    protected abstract Invoker doSelect(List<Invoker> invokers, RpcRequest request);

    /**
     * 计算权重的通用方法
     *
     * @param invoker Invoker实例
     * @return 权重值
     */
    protected int calculateWeight(Invoker invoker) {
        int weight = 100;

        // 根据平均响应时间调整权重
        double avgResponseTime = invoker.getAvgResponseTime();
        if (avgResponseTime > 0) {
            // 响应时间越长，权重越低
            weight = (int) Math.max(10, 100 * (1000.0 / (avgResponseTime + 1000.0)));
        }

        // 根据成功率调整权重
        double successRate = invoker.getSuccessRate();
        if (successRate < 1.0) {
            // 成功率越低，权重越低
            weight = (int) (weight * Math.max(0.1, successRate));
        }

        // 根据活跃请求数调整权重
        int activeCount = invoker.getActiveCount();
        if (activeCount > 5) {
            // 请求数越多，权重越低
            weight = weight / (activeCount / 5 + 1);
        }

        // 确保权重至少为1
        return Math.max(1, weight);
    }
}
