package com.weihua.rpc.core.server.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 固定窗口计数器限流实现
 */
@Slf4j
public class CounterRateLimit implements RateLimit {

    private final String interfaceName;
    private final int maxQps;
    
    // 计数器
    private final AtomicInteger counter = new AtomicInteger(0);
    
    // 上次重置时间
    private final AtomicLong lastResetTime;
    
    // 窗口大小(ms)，默认1秒
    private static final long WINDOW_SIZE_MS = 1000;
    
    // 统计
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong requestStartNanos = new AtomicLong(System.nanoTime());
    private static final long STATS_WINDOW_NANOS = 1_000_000_000L;

    public CounterRateLimit(String interfaceName, int maxQps) {
        this.interfaceName = interfaceName;
        this.maxQps = maxQps;
        this.lastResetTime = new AtomicLong(System.currentTimeMillis());
        
        log.info("创建计数器限流器: 接口={}, 最大QPS={}", interfaceName, maxQps);
    }

    @Override
    public boolean allowRequest() {
        long currentTime = System.currentTimeMillis();
        long lastReset = lastResetTime.get();
        
        // 检查是否需要重置计数器
        if (currentTime - lastReset >= WINDOW_SIZE_MS) {
            // CAS更新上次重置时间，避免并发问题
            if (lastResetTime.compareAndSet(lastReset, currentTime)) {
                counter.set(0);
                log.trace("重置计数器: 接口={}, 上次重置={}", interfaceName, lastReset);
            }
        }
        
        // 尝试增加计数器
        int currentCount = counter.incrementAndGet();
        
        // 检查是否超过限制
        if (currentCount <= maxQps) {
            // 更新统计信息
            updateStats();
            return true;
        } else {
            // 计数回退
            counter.decrementAndGet(); 
            log.debug("接口 {} 触发限流，请求计数={}/{}", interfaceName, currentCount, maxQps);
            updateStats();
            return false;
        }
    }
    
    private void updateStats() {
        requestCount.incrementAndGet();
        long currentNanos = System.nanoTime();
        long startNanos = requestStartNanos.get();
        
        if (currentNanos - startNanos >= STATS_WINDOW_NANOS) {
            if (requestStartNanos.compareAndSet(startNanos, currentNanos)) {
                requestCount.set(1);
            }
        }
    }

    @Override
    public String getInterfaceName() {
        return interfaceName;
    }

    @Override
    public int getMaxQps() {
        return maxQps;
    }

    @Override
    public double getCurrentQps() {
        long currentNanos = System.nanoTime();
        long startNanos = requestStartNanos.get();
        long windowNanos = currentNanos - startNanos;
        
        if (windowNanos <= 0) {
            return 0;
        }
        
        return (double) requestCount.get() * STATS_WINDOW_NANOS / windowNanos;
    }
}