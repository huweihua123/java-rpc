package com.weihua.client.serverCenter.balance.impl;

import com.weihua.client.invoker.Invoker;
import com.weihua.client.serverCenter.balance.LoadBalance;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class RoundLoadBalance implements LoadBalance {
    // 全局计数器（旧实现）
    private int choose;

    // 按服务名存储的计数器，用于Invoker级别的轮询
    private final Map<String, AtomicInteger> counterMap = new ConcurrentHashMap<>();

    // 活跃请求阈值 - 超过此值将跳过该Invoker
    private static final int ACTIVE_REQUEST_THRESHOLD = 50;

    // 响应时间阈值（毫秒） - 超过此值将降低选择概率
    private static final double RESPONSE_TIME_THRESHOLD = 200.0;

    @Override
    public String balance(List<String> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        choose++;
        choose = choose % addressList.size();
        log.info("轮询负载均衡选择了{}号服务器", choose);
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

        // 为每个服务接口使用独立的计数器
        String serviceName = request.getInterfaceName();
        AtomicInteger counter = counterMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));

        // 筛选状态良好的Invoker
        List<Invoker> healthyInvokers = new ArrayList<>();
        for (Invoker invoker : invokers) {
            // 跳过高负载的Invoker
            if (invoker.getActiveCount() > ACTIVE_REQUEST_THRESHOLD) {
                log.debug("Invoker {} 活跃请求数({})超过阈值({}), 暂时跳过",
                        invoker.getId(), invoker.getActiveCount(), ACTIVE_REQUEST_THRESHOLD);
                continue;
            }

            // 跳过响应时间过长的Invoker
            if (invoker.getAvgResponseTime() > RESPONSE_TIME_THRESHOLD && invoker.getSuccessRate() < 0.95) {
                log.debug("Invoker {} 性能不佳(响应时间: {}ms, 成功率: {}%), 降低选择优先级",
                        invoker.getId(),
                        String.format("%.2f", invoker.getAvgResponseTime()),
                        String.format("%.2f", invoker.getSuccessRate() * 100));

                // 不完全排除，但降低选中概率(1/3的概率仍然选择它)
                if (Math.random() > 0.33) {
                    continue;
                }
            }

            healthyInvokers.add(invoker);
        }

        // 如果筛选后没有合适的Invoker，则从原始列表中选择
        if (healthyInvokers.isEmpty()) {
            log.warn("没有状态良好的Invoker可用，从全部Invoker中选择");
            healthyInvokers = invokers;
        }

        // 轮询选择
        int index = Math.abs(counter.getAndIncrement() % healthyInvokers.size());
        Invoker selectedInvoker = healthyInvokers.get(index);

        log.info("智能轮询负载均衡选择Invoker: {}, 地址: {}, 活跃请求数: {}, 响应时间: {}ms, 成功率: {}%",
                selectedInvoker.getId(),
                selectedInvoker.getAddress(),
                selectedInvoker.getActiveCount(),
                String.format("%.2f", selectedInvoker.getAvgResponseTime()),
                String.format("%.2f", selectedInvoker.getSuccessRate() * 100));

        return selectedInvoker;
    }
}
