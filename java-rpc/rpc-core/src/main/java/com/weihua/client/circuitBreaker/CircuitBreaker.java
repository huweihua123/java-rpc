package com.weihua.client.circuitBreaker;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

enum CircuitBreakerState {
    // 关闭，开启，半开启
    CLOSED, OPEN, HALF_OPEN
}

public class CircuitBreaker {
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @Getter
    private final int failureThreshold;
    @Getter
    private final double halfOpenSuccessRate;
    @Getter
    private final long retryTimePeriod;
    @Getter
    private final int maxHalfOpenRequests;
    private volatile long lastFailureTime = 0;

    public CircuitBreaker(int failureThreshold, double halfOpenSuccessRate,
            long retryTimePeriod, int maxHalfOpenRequests) {
        this.failureThreshold = failureThreshold;
        this.halfOpenSuccessRate = halfOpenSuccessRate;
        this.retryTimePeriod = retryTimePeriod;
        this.maxHalfOpenRequests = maxHalfOpenRequests;
    }

    public synchronized boolean allowRequest() {
        switch (state) {
            case OPEN:
                if (System.currentTimeMillis() - lastFailureTime > retryTimePeriod) {
                    state = CircuitBreakerState.HALF_OPEN;
                    resetCounts();
                }
                return false;
            case HALF_OPEN:
                return requestCount.getAndIncrement() < maxHalfOpenRequests;
            default:
                return true;
        }
    }

    public synchronized void recordSuccess() {
        if (state == CircuitBreakerState.HALF_OPEN) {
            successCount.incrementAndGet();
            if (requestCount.get() > 0 &&
                    (double) successCount.get() / requestCount.get() >= halfOpenSuccessRate) {
                state = CircuitBreakerState.CLOSED;
                resetCounts();
            }
        }
    }

    public synchronized void recordFailure() {
        switch (state) {
            case CLOSED:
                if (failureCount.incrementAndGet() >= failureThreshold) {
                    state = CircuitBreakerState.OPEN;
                    lastFailureTime = System.currentTimeMillis();
                }
                break;
            case HALF_OPEN:
                state = CircuitBreakerState.OPEN;
                lastFailureTime = System.currentTimeMillis();
                resetCounts();
                break;
        }
    }

    private void resetCounts() {
        failureCount.set(0);
        successCount.set(0);
        requestCount.set(0);
    }

    /**
     * 获取熔断器当前状态
     */
    public CircuitBreakerState getState() {
        return state;
    }

    /**
     * 获取当前失败计数
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取当前成功计数
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取当前总请求计数
     */
    public int getRequestCount() {
        return requestCount.get();
    }

    /**
     * 获取上次失败时间
     */
    public long getLastFailureTime() {
        return lastFailureTime;
    }
}