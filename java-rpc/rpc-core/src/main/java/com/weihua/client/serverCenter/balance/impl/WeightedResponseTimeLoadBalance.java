package com.weihua.client.serverCenter.balance.impl;

import com.weihua.client.invoker.Invoker;
import com.weihua.client.metrics.HeartbeatStats;
import com.weihua.client.netty.handler.HeartBeatHandler;
import com.weihua.client.serverCenter.balance.LoadBalance;
import common.message.RpcRequest;
import io.netty.channel.Channel;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 加权响应时间负载均衡 - 增强版
 * 综合考虑响应时间、成功率、活跃请求数和心跳状态
 */
@Log4j2
public class WeightedResponseTimeLoadBalance implements LoadBalance {
    private final Random random = new Random();

    // 权重配置
    private static final double RESPONSE_TIME_WEIGHT = 0.4; // 响应时间权重
    private static final double SUCCESS_RATE_WEIGHT = 0.2; // 成功率权重
    private static final double ACTIVE_WEIGHT = 0.1; // 活跃请求数权重
    private static final double HEARTBEAT_WEIGHT = 0.3; // 心跳状态权重

    // 响应时间基准值（毫秒）
    private static final double BASE_RESPONSE_TIME = 50.0;

    // 最大活跃请求数基准
    private static final int MAX_ACTIVE_REQUESTS = 100;

    // 心跳成功率阈值，低于该值的Invoker会被降级
    private static final double MIN_HEARTBEAT_SUCCESS_RATE = 0.7;

    @Override
    public String balance(List<String> addressList) {
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

        // 评分系统 - 计算每个Invoker的综合得分
        List<ScoredInvoker> scoredInvokers = new ArrayList<>(invokers.size());

        for (Invoker invoker : invokers) {
            double score = calculateScore(invoker);
            scoredInvokers.add(new ScoredInvoker(invoker, score));
        }

        // 过滤出心跳状况良好的Invoker
        List<ScoredInvoker> healthyInvokers = filterHealthyInvokers(scoredInvokers);

        // 如果没有健康的Invoker，则使用全部Invoker
        if (healthyInvokers.isEmpty()) {
            healthyInvokers = scoredInvokers;
        }

        // 根据分数排序，选择得分最高的
        healthyInvokers.sort((a, b) -> Double.compare(b.score, a.score));

        // 如果有多个得分相近的（差异小于10%），则随机选择其中之一以避免单点负载过高
        List<ScoredInvoker> candidates = new ArrayList<>();
        double highestScore = healthyInvokers.get(0).score;

        for (ScoredInvoker scored : healthyInvokers) {
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
            selected = healthyInvokers.get(0);
        }

        // 获取心跳状态信息
        HeartbeatStats heartbeatStats = getHeartbeatStats(selected.invoker);
        String heartbeatInfo = heartbeatStats != null
                ? String.format("心跳成功率: %.2f%%", heartbeatStats.getSuccessRate() * 100)
                : "心跳数据不可用";

        log.info("加权负载均衡选择Invoker: {}, 得分: {}, 响应时间: {}ms, 成功率: {}%, 活跃请求: {}, {}",
                selected.invoker.getId(),
                String.format("%.2f", selected.score),
                String.format("%.2f", selected.invoker.getAvgResponseTime()),
                String.format("%.2f", selected.invoker.getSuccessRate() * 100),
                String.valueOf(selected.invoker.getActiveCount()),
                heartbeatInfo);

        return selected.invoker;
    }

    /**
     * 过滤出心跳状况良好的Invoker
     */
    private List<ScoredInvoker> filterHealthyInvokers(List<ScoredInvoker> invokers) {
        List<ScoredInvoker> healthyInvokers = new ArrayList<>();

        for (ScoredInvoker scoredInvoker : invokers) {
            HeartbeatStats stats = getHeartbeatStats(scoredInvoker.invoker);

            // 如果心跳统计不可用或成功率高于阈值，则视为健康
            if (stats == null || stats.getSuccessRate() >= MIN_HEARTBEAT_SUCCESS_RATE) {
                healthyInvokers.add(scoredInvoker);
            }
        }

        return healthyInvokers;
    }

    /**
     * 计算Invoker的综合得分
     * 得分越高表示越适合被选中
     */
    private double calculateScore(Invoker invoker) {
        double avgResponseTime = invoker.getAvgResponseTime();
        double successRate = invoker.getSuccessRate();
        int activeCount = invoker.getActiveCount();

        // 获取心跳成功率
        double heartbeatSuccessRate = 0.0;
        HeartbeatStats heartbeatStats = getHeartbeatStats(invoker);
        if (heartbeatStats != null) {
            heartbeatSuccessRate = heartbeatStats.getSuccessRate();
        } else {
            // 如果无法获取心跳统计，假设心跳状况良好
            heartbeatSuccessRate = 1.0;
        }

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

        // 4. 心跳得分 (直接使用心跳成功率)
        double heartbeatScore = heartbeatSuccessRate;

        // 5. 计算综合得分
        return responseTimeScore * RESPONSE_TIME_WEIGHT +
                successRate * SUCCESS_RATE_WEIGHT +
                activeScore * ACTIVE_WEIGHT +
                heartbeatScore * HEARTBEAT_WEIGHT;
    }

    /**
     * 获取Invoker对应的心跳统计信息
     */
    private HeartbeatStats getHeartbeatStats(Invoker invoker) {
        try {
            // 尝试通过反射获取Invoker的Channel
            // 注意：这依赖于ChannelInvoker的实现细节
            Field channelField = invoker.getClass().getDeclaredField("channel");
            channelField.setAccessible(true);
            Channel channel = (Channel) channelField.get(invoker);

            if (channel != null) {
                // 从Channel的Pipeline中获取HeartBeatHandler
                HeartBeatHandler handler = channel.pipeline().get(HeartBeatHandler.class);
                if (handler != null) {
                    // 获取心跳统计信息
                    return handler.getHeartbeatStats(channel);
                }
            }
        } catch (Exception e) {
            // 忽略异常，如果无法获取心跳统计，返回null
        }
        return null;
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
