package client.circuitBreaker;

import java.util.concurrent.atomic.AtomicInteger;

import static client.circuitBreaker.CircuitBreakerState.CLOSED;
import static client.circuitBreaker.CircuitBreakerState.HALF_OPEN;

enum CircuitBreakerState {
    //关闭，开启，半开启
    CLOSED, OPEN, HALF_OPEN
}
public class CircuitBreaker {
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    private final int failureThreshold;
    private final double halfOpenSuccessRate;
    private final long retryTimePeriod;
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
}