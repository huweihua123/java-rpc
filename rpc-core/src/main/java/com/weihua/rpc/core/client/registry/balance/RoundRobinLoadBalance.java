/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:02:09
 * @LastEditTime: 2025-04-10 18:51:24
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry.balance;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡实现
 */
@Component("roundRobinLoadBalance")
@ConditionalOnExpression("'${rpc.mode:server}'.equals('client') && '${rpc.loadBalance.type:random}'.equals('roundrobin')")
// @ConditionalOnExpression("#{environment['rpc.mode'] == 'client' &&
// environment['rpc.loadBalance.type'] == 'random'}")
public class RoundRobinLoadBalance extends AbstractLoadBalance {

    // 服务计数器映射
    private final Map<String, AtomicInteger> counterMap = new ConcurrentHashMap<>();

    @Override
    protected Invoker doSelect(List<Invoker> invokers, RpcRequest request) {
        String serviceName = request.getInterfaceName();

        // 获取或创建计数器
        AtomicInteger counter = counterMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));

        // 计算总权重
        int totalWeight = 0;
        for (Invoker invoker : invokers) {
            totalWeight += calculateWeight(invoker);
        }

        // 如果所有权重相同，则直接轮询
        if (totalWeight == invokers.size()) {
            int index = counter.getAndIncrement() % invokers.size();
            if (index < 0) {
                index = 0;
            }
            return invokers.get(index);
        }

        // 修正计数器，避免溢出
        if (counter.get() > 10000) {
            counter.set(0);
        }

        // 加权轮询
        int currentIndex = counter.getAndIncrement() % totalWeight;

        // 根据权重选择
        int weightSum = 0;
        for (Invoker invoker : invokers) {
            weightSum += calculateWeight(invoker);
            if (currentIndex < weightSum) {
                return invoker;
            }
        }

        // 如果没有选中，返回第一个可用的
        return invokers.get(0);
    }
}
