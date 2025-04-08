package com.weihua.client.metrics;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 心跳统计信息类
 * 记录心跳成功率、失败率和响应时间等指标
 */
public class HeartbeatStats {
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    @Getter
    private volatile long lastSuccessTime = 0;
    @Getter
    private volatile long lastFailureTime = 0;
    @Getter
    private volatile double avgResponseTime = 0;

    /**
     * 记录心跳成功
     */
    public void recordSuccess() {
        recordSuccess(System.currentTimeMillis());
    }

    /**
     * 记录心跳成功和响应时间
     */
    public void recordSuccess(long currentTime) {
        totalCount.incrementAndGet();
        successCount.incrementAndGet();
        lastSuccessTime = currentTime;
    }

    /**
     * 记录心跳成功和响应时间
     */
    public void recordSuccess(long responseTime, long currentTime) {
        recordSuccess(currentTime);

        // 更新平均响应时间
        long total = totalResponseTime.addAndGet(responseTime);
        int count = successCount.get();
        if (count > 0) {
            avgResponseTime = (double) total / count;
        }
    }

    /**
     * 记录心跳失败
     */
    public void recordFailure() {
        totalCount.incrementAndGet();
        failureCount.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
    }

    /**
     * 获取心跳成功率
     */
    public double getSuccessRate() {
        int total = totalCount.get();
        if (total == 0) {
            return 1.0; // 默认为1.0，表示100%
        }
        return (double) successCount.get() / total;
    }

    /**
     * 获取心跳失败率
     */
    public double getFailureRate() {
        int total = totalCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) failureCount.get() / total;
    }

    /**
     * 获取总心跳次数
     */
    public int getTotalCount() {
        return totalCount.get();
    }

    /**
     * 获取成功心跳次数
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取失败心跳次数
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 重置统计数据
     */
    public void reset() {
        totalCount.set(0);
        successCount.set(0);
        failureCount.set(0);
        totalResponseTime.set(0);
        avgResponseTime = 0;
    }
}
