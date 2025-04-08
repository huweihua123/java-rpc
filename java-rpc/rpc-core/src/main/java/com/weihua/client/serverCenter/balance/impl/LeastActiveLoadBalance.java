/*
 * @Author: weihua hu
 * @Date: 2025-04-06 18:11:31
 * @LastEditTime: 2025-04-06 20:04:36
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.serverCenter.balance.impl;

import com.weihua.client.invoker.Invoker;
import com.weihua.client.serverCenter.balance.LoadBalance;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 最少活跃请求数负载均衡 - 增强版
 * 在活跃请求数相同的情况下，考虑响应时间和成功率
 */
@Log4j2
public class LeastActiveLoadBalance implements LoadBalance {
    private final Random random = new Random();

    // 成功率阈值，低于此值的将降低选择优先级
    private static final double MIN_SUCCESS_RATE = 0.95;

    // 响应时间阈值，超过此值将降低选择优先级
    private static final double MAX_RESPONSE_TIME = 200.0;

    @Override
    public String balance(List<String> addressList) {
        // 兼容旧接口
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        int index = random.nextInt(addressList.size());
        log.info("随机负载均衡选择了{}号服务器", index);
        return addressList.get(index);
    }

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }

        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        // 找出活跃数最小的Invoker
        int leastActive = -1;
        int leastCount = 0;
        Invoker[] leastActiveInvokers = new Invoker[invokers.size()];

        // 找出最小活跃数的Invoker列表
        for (Invoker invoker : invokers) {
            int active = invoker.getActiveCount();

            if (leastActive == -1 || active < leastActive) {
                // 发现更小的活跃数，重新开始计数
                leastActive = active;
                leastCount = 1;
                leastActiveInvokers[0] = invoker;
            } else if (active == leastActive) {
                // 活跃数相同，累加
                leastActiveInvokers[leastCount++] = invoker;
            }
        }

        // 如果只有一个最小则直接返回
        if (leastCount == 1) {
            log.info("负载均衡选择最少活跃请求Invoker: {}, 活跃请求数: {}",
                    leastActiveInvokers[0].getAddress(), leastActive);
            return leastActiveInvokers[0];
        }

        // 如果有多个具有相同最小活跃数，则基于性能指标进一步筛选
        List<Invoker> candidates = new ArrayList<>(leastCount);

        // 先筛选出性能良好的候选者
        for (int i = 0; i < leastCount; i++) {
            Invoker invoker = leastActiveInvokers[i];

            // 忽略性能不佳的Invoker
            if (invoker.getSuccessRate() < MIN_SUCCESS_RATE &&
                    invoker.getAvgResponseTime() > MAX_RESPONSE_TIME) {
                log.debug("Invoker {} 性能不佳(响应时间: {}ms, 成功率: {}%), 排除",
                        invoker.getId(),
                        String.format("%.2f", invoker.getAvgResponseTime()),
                        String.format("%.2f", invoker.getSuccessRate() * 100));
                continue;
            }

            candidates.add(invoker);
        }

        // 如果筛选后没有候选者，则使用全部最小活跃数Invoker
        if (candidates.isEmpty()) {
            for (int i = 0; i < leastCount; i++) {
                candidates.add(leastActiveInvokers[i]);
            }
        }

        // 在剩余候选中继续选择
        Invoker selectedInvoker;

        if (candidates.size() == 1) {
            selectedInvoker = candidates.get(0);
        } else {
            // 如果有多个候选，按响应时间排序，选择响应最快的那个
            candidates.sort((a, b) -> Double.compare(a.getAvgResponseTime(), b.getAvgResponseTime()));

            // 选择前20%响应最快的Invoker中的随机一个
            int selectCount = Math.max(1, candidates.size() / 5);
            int selectIndex = random.nextInt(selectCount);
            selectedInvoker = candidates.get(selectIndex);
        }

        log.info("增强版最少活跃请求负载均衡选择Invoker: {}, 活跃请求数: {}, 响应时间: {}ms, 成功率: {}%",
                selectedInvoker.getAddress(),
                selectedInvoker.getActiveCount(),
                String.format("%.2f", selectedInvoker.getAvgResponseTime()),
                String.format("%.2f", selectedInvoker.getSuccessRate() * 100));

        return selectedInvoker;
    }
}