package com.weihua.rpc.core.server.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 漏桶算法限流实现
 */
@Slf4j
public class LeakyBucketRateLimit implements RateLimit {

    private final String interfaceName;
    private final int maxQps;
    
    // 漏桶容量
    private final int capacity;
    
    // 漏水速率（每毫秒）
    private final double leakRatePerMs;
    
    // 当前水量
    private double currentWater;
    
    // 上次漏水时间
    private long lastLeakTimestamp;
    
    // 锁，保护并发修改
    private final Lock lock = new ReentrantLock();
    
    // 统计
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong requestStartNanos = new AtomicLong(System.nanoTime());
    private static final long STATS_WINDOW_NANOS = 1_000_000_000L;

    public LeakyBucketRateLimit(String interfaceName, int maxQps) {
        this.interfaceName = interfaceName;
        this.maxQps = maxQps;
        this.capacity = maxQps;
        this.leakRatePerMs = maxQps / 1000.0; // 每毫秒漏水速率
        this.currentWater = 0;
        this.lastLeakTimestamp = System.currentTimeMillis();
        
        log.info("创建漏桶限流器: 接口={}, 最大QPS={}", interfaceName, maxQps);
    }

    @Override
    public boolean allowRequest() {
        lock.lock();
        try {
            // 计算漏出的水量
            long now = System.currentTimeMillis();
            long timeElapsed = now - lastLeakTimestamp;
            
            // 更新当前水量和时间
            double leaked = timeElapsed * leakRatePerMs;
            currentWater = Math.max(0, currentWater - leaked);
            lastLeakTimestamp = now;
            
            // 判断是否可以加入请求
            if (currentWater < capacity) {
                // 可以加水
                currentWater++;
                updateStats();
                return true;
            } else {
                // 水满，拒绝请求
                log.debug("接口 {} 触发限流，漏桶已满", interfaceName);
                updateStats();
                return false;
            }
        } finally {
            lock.unlock();
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