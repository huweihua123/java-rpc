/*
 * @Author: weihua hu
 * @Date: 2025-03-21 20:26:03
 * @LastEditTime: 2025-04-06 20:04:53
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.serverCenter.balance.impl;

import com.weihua.client.invoker.Invoker;
import com.weihua.client.serverCenter.balance.LoadBalance;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Random;

@Log4j2
public class RandomBalance implements LoadBalance {
    private final Random random = new Random();

    // 参考基准值
    private static final double BASE_RESPONSE_TIME = 50.0; // 毫秒
    private static final int MAX_ACTIVE_REQUESTS = 100; // 最大活跃请求数

    @Override
    public String balance(List<String> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        int choose = random.nextInt(addressList.size());
        log.info("随机负载均衡选择了{}号服务器", choose);
        return addressList.get(choose);
    }

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }

        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        // 计算每个Invoker的权重分数
        double totalWeight = 0;
        double[] weights = new double[invokers.size()];

        for (int i = 0; i < invokers.size(); i++) {
            Invoker invoker = invokers.get(i);
            double weight = calculateWeight(invoker);
            weights[i] = weight;
            totalWeight += weight;
        }

        // 根据权重随机选择
        double randomValue = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0;
        int selectedIndex = 0;

        for (int i = 0; i < weights.length; i++) {
            cumulativeWeight += weights[i];
            if (randomValue <= cumulativeWeight) {
                selectedIndex = i;
                break;
            }
        }

        Invoker selectedInvoker = invokers.get(selectedIndex);

        log.info("加权随机负载均衡选择Invoker: {}, 权重: {}, 地址: {}, 活跃请求数: {}, 响应时间: {}ms",
                selectedInvoker.getId(),
                String.format("%.2f", weights[selectedIndex]),
                selectedInvoker.getAddress(),
                selectedInvoker.getActiveCount(),
                String.format("%.2f", selectedInvoker.getAvgResponseTime()));

        return selectedInvoker;
    }

    /**
     * 计算Invoker的权重分数，分数越高越容易被选中
     */
    private double calculateWeight(Invoker invoker) {
        // 1. 获取性能指标
        double responseTime = Math.max(1.0, invoker.getAvgResponseTime());
        double successRate = Math.max(0.1, invoker.getSuccessRate()); // 防止除零
        int activeCount = invoker.getActiveCount();

        // 2. 响应时间权重 (响应时间越短，权重越高)
        double responseTimeWeight = responseTime <= 0 ? 1.0 : BASE_RESPONSE_TIME / responseTime;

        // 3. 成功率权重 (直接使用成功率)
        double successRateWeight = successRate;

        // 4. 活跃请求数权重 (活跃请求越少，权重越高)
        double activeCountWeight = 1.0 - Math.min(1.0, (double) activeCount / MAX_ACTIVE_REQUESTS);

        // 5. 组合权重 (根据实际情况可以调整各因素的权重占比)
        return (responseTimeWeight * 0.5) + (successRateWeight * 0.3) + (activeCountWeight * 0.2);
    }
}
