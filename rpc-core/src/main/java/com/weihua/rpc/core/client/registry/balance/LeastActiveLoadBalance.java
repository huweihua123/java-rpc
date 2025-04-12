/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:02:53
 * @LastEditTime: 2025-04-12 14:09:10
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry.balance;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;
import com.weihua.rpc.core.condition.ConditionalOnClientMode;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 最小活跃数负载均衡实现
 */
@Component("leastActiveLoadBalance")
// @ConditionalOnExpression("'${rpc.mode:server}'.equals('client') &&'${rpc.loadBalance.type:random}'.equals('leastactive')")
@ConditionalOnClientMode
@ConditionalOnProperty(name = "rpc.loadBalance.type", havingValue = "leastactive", matchIfMissing = false)
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    @Override
    protected Invoker doSelect(List<Invoker> invokers, RpcRequest request) {
        // 找出最小活跃数
        int leastActive = Integer.MAX_VALUE;
        for (Invoker invoker : invokers) {
            int active = invoker.getActiveCount();
            if (active < leastActive) {
                leastActive = active;
            }
        }

        // 收集所有具有最小活跃数的调用者
        List<Invoker> leastActiveInvokers = new ArrayList<>();
        int totalWeight = 0;
        for (Invoker invoker : invokers) {
            if (invoker.getActiveCount() == leastActive) {
                leastActiveInvokers.add(invoker);
                totalWeight += calculateWeight(invoker);
            }
        }

        // 如果只有一个最小活跃数的调用者，直接返回
        if (leastActiveInvokers.size() == 1) {
            return leastActiveInvokers.get(0);
        }

        // 如果有多个最小活跃数的调用者，使用加权随机选择
        if (totalWeight > 0) {
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);

            // 根据权重选择
            for (Invoker invoker : leastActiveInvokers) {
                offset -= calculateWeight(invoker);
                if (offset < 0) {
                    return invoker;
                }
            }
        }

        // 如果权重都是0，随机选择一个
        return leastActiveInvokers.get(ThreadLocalRandom.current().nextInt(leastActiveInvokers.size()));
    }
}
