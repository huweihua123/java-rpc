/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:01:51
 * @LastEditTime: 2025-04-15 00:12:52
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry.balance.impl;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡实现
 */
public class RandomLoadBalance extends AbstractLoadBalance {

    @Override
    protected Invoker doSelect(List<Invoker> invokers, RpcRequest request) {
        // 计算总权重
        int totalWeight = 0;
        for (Invoker invoker : invokers) {
            totalWeight += calculateWeight(invoker);
        }

        // 如果所有权重相同，则直接随机选择
        if (totalWeight == invokers.size()) {
            return invokers.get(ThreadLocalRandom.current().nextInt(invokers.size()));
        }

        // 加权随机选择
        int offset = ThreadLocalRandom.current().nextInt(totalWeight);

        // 根据权重选择
        for (Invoker invoker : invokers) {
            int weight = calculateWeight(invoker);
            offset -= weight;
            if (offset < 0) {
                return invoker;
            }
        }

        // 如果没有选中，返回第一个可用的
        return invokers.get(0);
    }
}
