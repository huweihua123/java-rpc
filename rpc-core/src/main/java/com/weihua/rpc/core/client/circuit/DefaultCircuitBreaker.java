package com.weihua.rpc.core.client.circuit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认熔断器实现
 */
@Slf4j
public class DefaultCircuitBreaker implements CircuitBreaker {

    // 熔断器配置
    private final int failureThreshold; // 连续失败阈值
    private final double failureRateThreshold; // 错误率阈值（例如：0.5表示50%）
    private final long resetTimeoutMs; // 熔断器重置超时（毫秒）
    private final int halfOpenMaxRequests; // 半开状态最大请求数

    // 熔断器状态
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private LocalDateTime lastStateChangeTime = LocalDateTime.now();

    // 计数器
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
    private final AtomicInteger halfOpenRequests = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);

    // 指标收集窗口（毫秒）
    private static final long METRICS_ROLLING_WINDOW_MS = 60000; // 60秒窗口期
    private final AtomicLong lastMetricsResetTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 创建熔断器
     *
     * @param failureThreshold     连续失败阈值
     * @param failureRateThreshold 失败率阈值（0-1之间）
     * @param resetTimeoutMs       重置超时（毫秒）
     * @param halfOpenMaxRequests  半开状态最大请求数
     */
    public DefaultCircuitBreaker(int failureThreshold, double failureRateThreshold,
            long resetTimeoutMs, int halfOpenMaxRequests) {
        this.failureThreshold = failureThreshold;
        this.failureRateThreshold = failureRateThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.halfOpenMaxRequests = halfOpenMaxRequests;
        log.info("创建熔断器, 失败阈值={}, 错误率阈值={}%, 重置超时={}ms, 半开最大请求={}",
                failureThreshold, failureRateThreshold * 100, resetTimeoutMs, halfOpenMaxRequests);
    }

    @Override
    public boolean allowRequest() {
        rollMetricsWindowIfNeeded();

        State currentState = state.get();
        switch (currentState) {
            case OPEN:
                // 检查是否已过重置超时
                if (isTimeoutExpired()) {
                    log.info("熔断器超时已过期，状态从OPEN变为HALF_OPEN");
                    transitionToHalfOpen();
                    return checkHalfOpenRequest();
                }
                return false;

            case HALF_OPEN:
                return checkHalfOpenRequest();

            case CLOSED:
            default:
                return true;
        }
    }

    /**
     * 检查半开状态下是否允许请求
     */
    private boolean checkHalfOpenRequest() {
        // 在半开状态下，只允许有限数量的请求通过
        int currentRequests = halfOpenRequests.incrementAndGet();
        return currentRequests <= halfOpenMaxRequests;
    }

    /**
     * 检查超时是否已过期
     */
    private boolean isTimeoutExpired() {
        long elapsedMs = java.time.Duration.between(lastStateChangeTime, LocalDateTime.now()).toMillis();
        return elapsedMs >= resetTimeoutMs;
    }

    @Override
    public void recordSuccess() {
        rollMetricsWindowIfNeeded();

        totalRequests.incrementAndGet();
        consecutiveFailures.set(0);

        if (state.get() == State.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            if (successes >= halfOpenMaxRequests) {
                log.info("半开状态下累积了足够的成功请求，熔断器关闭");
                transitionToClosed();
            }
        }
    }

    @Override
    public void recordFailure() {
        rollMetricsWindowIfNeeded();

        totalRequests.incrementAndGet();
        totalFailures.incrementAndGet();

        int failures = consecutiveFailures.incrementAndGet();

        // 检查错误率
        checkErrorRateAndTrip();

        // 检查连续失败次数
        if (failures >= failureThreshold) {
            if (state.get() == State.CLOSED) {
                log.warn("连续失败次数达到阈值({}), 熔断器打开", failures);
                transitionToOpen();
            } else if (state.get() == State.HALF_OPEN) {
                log.warn("半开状态下失败, 熔断器重新打开");
                transitionToOpen();
            }
        }
    }

    /**
     * 检查错误率是否超出阈值，如果是则触发熔断
     */
    private void checkErrorRateAndTrip() {
        long total = totalRequests.get();
        long failures = totalFailures.get();

        if (total >= 10) { // 至少需要一定样本量
            double failureRate = (double) failures / total;
            if (failureRate >= failureRateThreshold && state.get() == State.CLOSED) {
                log.warn("错误率({})超过阈值({}), 熔断器打开",
                        String.format("%.2f", failureRate * 100) + "%",
                        String.format("%.2f", failureRateThreshold * 100) + "%");
                transitionToOpen();
            }
        }
    }

    /**
     * 重置指标窗口（如果需要）
     */
    private void rollMetricsWindowIfNeeded() {
        long now = System.currentTimeMillis();
        long lastReset = lastMetricsResetTime.get();

        if (now - lastReset > METRICS_ROLLING_WINDOW_MS) {
            if (lastMetricsResetTime.compareAndSet(lastReset, now)) {
                totalRequests.set(0);
                totalFailures.set(0);
            }
        }
    }

    /**
     * 转换到开路状态
     */
    private void transitionToOpen() {
        if (state.compareAndSet(State.CLOSED, State.OPEN) ||
                state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            resetHalfOpenCounters();
            lastStateChangeTime = LocalDateTime.now();
        }
    }

    /**
     * 转换到半开状态
     */
    private void transitionToHalfOpen() {
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            resetHalfOpenCounters();
            lastStateChangeTime = LocalDateTime.now();
        }
    }

    /**
     * 转换到关闭状态
     */
    private void transitionToClosed() {
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            resetHalfOpenCounters();
            lastStateChangeTime = LocalDateTime.now();
            consecutiveFailures.set(0);
        }
    }

    /**
     * 重置半开状态计数器
     */
    private void resetHalfOpenCounters() {
        halfOpenRequests.set(0);
        halfOpenSuccesses.set(0);
    }

    @Override
    public State getState() {
        // 如果是OPEN状态，可能需要自动转为HALF_OPEN
        if (state.get() == State.OPEN && isTimeoutExpired()) {
            transitionToHalfOpen();
        }
        return state.get();
    }

    /**
     * 获取当前错误率
     * 
     * @return 错误率(0-1)
     */
    public double getErrorRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) totalFailures.get() / total;
    }
}
