/*
 * @Author: weihua hu
 * @Date: 2025-04-13 14:37:39
 * @LastEditTime: 2025-04-15 00:13:11
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry.balance.impl;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;

import lombok.extern.slf4j.Slf4j;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 综合指标加权负载均衡实现
 * 综合考虑响应时间、成功率和活跃请求数
 */
@Slf4j
public class WeightedMetricsLoadBalance extends AbstractLoadBalance {

    private final Random random = new Random();

    // 权重配置
    private static final double RESPONSE_TIME_WEIGHT = 0.5; // 响应时间权重
    private static final double SUCCESS_RATE_WEIGHT = 0.3; // 成功率权重
    private static final double ACTIVE_WEIGHT = 0.2; // 活跃请求数权重

    // 响应时间基准值（毫秒）
    private static final double BASE_RESPONSE_TIME = 50.0;

    // 最大活跃请求数基准
    private static final int MAX_ACTIVE_REQUESTS = 100;

    @Override
    protected Invoker doSelect(List<Invoker> invokers, RpcRequest request) {
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }

        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        // 评分系统 - 计算每个Invoker的综合得分
        List<ScoredInvoker> scoredInvokers = new ArrayList<>(invokers.size());

        for (Invoker invoker : invokers) {
            double score = calculateScore(invoker);
            scoredInvokers.add(new ScoredInvoker(invoker, score));
        }

        // 根据分数排序，选择得分最高的
        scoredInvokers.sort((a, b) -> Double.compare(b.score, a.score));

        // 如果有多个得分相近的（差异小于10%），则随机选择其中之一以避免单点负载过高
        List<ScoredInvoker> candidates = new ArrayList<>();
        double highestScore = scoredInvokers.get(0).score;

        for (ScoredInvoker scored : scoredInvokers) {
            if (scored.score >= highestScore * 0.9) {
                candidates.add(scored);
            } else {
                break;
            }
        }

        ScoredInvoker selected;
        if (candidates.size() > 1) {
            // 从候选中随机选择
            selected = candidates.get(random.nextInt(candidates.size()));
        } else {
            // 直接选择得分最高的
            selected = scoredInvokers.get(0);
        }

        log.info("综合负载均衡选择Invoker: {}, 得分: {}, 响应时间: {}ms, 成功率: {}%, 活跃请求: {}, 总请求: {}",
                selected.invoker.getId(),
                String.format("%.2f", selected.score),
                String.format("%.2f", selected.invoker.getAvgResponseTime()),
                String.format("%.2f", selected.invoker.getSuccessRate() * 100),
                selected.invoker.getActiveCount(),
                selected.invoker.getRequestCount());

        return selected.invoker;
    }

    /**
     * 计算Invoker的综合得分
     * 得分越高表示越适合被选中
     */
    private double calculateScore(Invoker invoker) {
        double avgResponseTime = invoker.getAvgResponseTime();
        double successRate = invoker.getSuccessRate();
        int activeCount = invoker.getActiveCount();

        // 1. 响应时间得分 (响应时间越短得分越高，呈反比关系)
        // 如果响应时间为0（无历史数据），设置为基准值
        if (avgResponseTime <= 0) {
            avgResponseTime = BASE_RESPONSE_TIME;
        }
        double responseTimeScore = BASE_RESPONSE_TIME / avgResponseTime;

        // 2. 成功率得分 (直接使用成功率)
        // 如果成功率为0（无历史数据），设置为1.0
        if (successRate <= 0) {
            successRate = 1.0;
        }

        // 3. 活跃请求数得分 (活跃请求越少得分越高，呈反比关系)
        double activeScore = 1.0 - Math.min(1.0, (double) activeCount / MAX_ACTIVE_REQUESTS);

        // 4. 计算综合得分
        return responseTimeScore * RESPONSE_TIME_WEIGHT +
                successRate * SUCCESS_RATE_WEIGHT +
                activeScore * ACTIVE_WEIGHT;
    }

    /**
     * 带分数的Invoker封装类
     */
    private static class ScoredInvoker {
        final Invoker invoker;
        final double score;

        ScoredInvoker(Invoker invoker, double score) {
            this.invoker = invoker;
            this.score = score;
        }
    }
}