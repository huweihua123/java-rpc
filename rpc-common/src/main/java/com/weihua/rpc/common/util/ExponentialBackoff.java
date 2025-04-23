package com.weihua.rpc.common.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避算法工具类
 * 提供可复用的重试间隔计算逻辑
 */
public class ExponentialBackoff {
    
    private final int baseIntervalMs;       // 基础重试间隔（毫秒）
    private final double multiplier;        // 指数退避乘数
    private final int maxIntervalMs;        // 最大退避时间（毫秒）
    private final int minIntervalMs;        // 最小重试间隔（毫秒）
    private final boolean addJitter;        // 是否添加随机抖动
    private final double jitterFactor;      // 抖动因子(0-1之间的值)

    /**
     * 使用Builder模式创建ExponentialBackoff实例
     */
    private ExponentialBackoff(Builder builder) {
        this.baseIntervalMs = builder.baseIntervalMs;
        this.multiplier = builder.multiplier;
        this.maxIntervalMs = builder.maxIntervalMs;
        this.minIntervalMs = builder.minIntervalMs;
        this.addJitter = builder.addJitter;
        this.jitterFactor = builder.jitterFactor;
    }

    /**
     * 计算下一次重试的间隔时间
     *
     * @param retryCount 当前重试次数
     * @return 下一次重试的间隔时间（毫秒）
     */
    public int calculateDelayMillis(int retryCount) {
        // 基础重试间隔 * (退避乘数 ^ 重试次数)
        double expBackoff = baseIntervalMs * Math.pow(multiplier, retryCount);

        // 确保不小于最小重试间隔
        expBackoff = Math.max(expBackoff, minIntervalMs);

        // 限制最大退避时间
        int nextInterval = (int) Math.min(expBackoff, maxIntervalMs);

        // 添加随机抖动
        if (addJitter) {
            double jitter = jitterFactor * nextInterval * ThreadLocalRandom.current().nextDouble();
            nextInterval = (int) (nextInterval + jitter);
        }

        return nextInterval;
    }
    
    /**
     * 判断是否应该进行重试
     * 
     * @param lastRetryTime 上次重试时间戳
     * @param retryCount 已重试次数
     * @param maxRetries 最大重试次数
     * @return 是否应该重试
     */
    public boolean shouldRetry(long lastRetryTime, int retryCount, int maxRetries) {
        // 超过最大重试次数
        if (retryCount >= maxRetries) {
            return false;
        }
        
        // 首次重试
        if (lastRetryTime == 0) {
            return true;
        }
        
        // 计算退避时间
        int backoffTime = calculateDelayMillis(retryCount);
        
        // 检查是否满足退避时间要求
        long now = System.currentTimeMillis();
        long elapsed = now - lastRetryTime;
        
        return elapsed >= backoffTime;
    }
    
    /**
     * 创建Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder类，用于构建ExponentialBackoff实例
     */
    public static class Builder {
        private int baseIntervalMs = 1000;      // 默认1秒
        private double multiplier = 2.0;        // 默认翻倍增长
        private int maxIntervalMs = 60000;      // 默认最大1分钟
        private int minIntervalMs = 100;        // 默认最小100ms
        private boolean addJitter = true;       // 默认添加抖动
        private double jitterFactor = 0.2;      // 默认20%抖动

        public Builder baseIntervalMs(int baseIntervalMs) {
            this.baseIntervalMs = baseIntervalMs;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxIntervalMs(int maxIntervalMs) {
            this.maxIntervalMs = maxIntervalMs;
            return this;
        }

        public Builder minIntervalMs(int minIntervalMs) {
            this.minIntervalMs = minIntervalMs;
            return this;
        }

        public Builder addJitter(boolean addJitter) {
            this.addJitter = addJitter;
            return this;
        }
        
        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = Math.max(0, Math.min(1, jitterFactor)); // 限制在0-1之间
            return this;
        }

        public ExponentialBackoff build() {
            return new ExponentialBackoff(this);
        }
    }
}