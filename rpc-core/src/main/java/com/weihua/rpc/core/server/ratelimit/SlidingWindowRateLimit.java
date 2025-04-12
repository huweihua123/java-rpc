package com.weihua.rpc.core.server.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 滑动窗口限流实现
 */
@Slf4j
public class SlidingWindowRateLimit implements RateLimit {

    private final String interfaceName;
    private final int maxQps;
    
    // 滑动窗口大小 (10个子窗口组成1秒)
    private static final int WINDOW_COUNT = 10;
    
    // 窗口大小 (ms)
    private static final int WINDOW_SIZE_MS = 1000 / WINDOW_COUNT;
    
    // 单个窗口最大请求数
    private final int maxRequestsPerWindow;
    
    // 窗口计数器
    private final AtomicInteger[] windowCounters;
    
    // 当前窗口索引
    private AtomicInteger currentWindowIndex;
    
    // 上次窗口重置时间
    private AtomicLong lastResetTime;
    
    // 统计
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong requestStartNanos = new AtomicLong(System.nanoTime());
    private static final long STATS_WINDOW_NANOS = 1_000_000_000L;

    public SlidingWindowRateLimit(String interfaceName, int maxQps) {
        this.interfaceName = interfaceName;
        this.maxQps = maxQps;
        this.maxRequestsPerWindow = maxQps / WINDOW_COUNT;
        
        // 初始化窗口计数器
        this.windowCounters = new AtomicInteger[WINDOW_COUNT];
        for (int i = 0; i < WINDOW_COUNT; i++) {
            this.windowCounters[i] = new AtomicInteger(0);
        }
        
        this.currentWindowIndex = new AtomicInteger(0);
        this.lastResetTime = new AtomicLong(System.currentTimeMillis());
        
        log.info("创建滑动窗口限流器: 接口={}, 最大QPS={}, 窗口数={}", 
                interfaceName, maxQps, WINDOW_COUNT);
    }

    @Override
    public boolean allowRequest() {
        // 检查是否需要滑动窗口
        slidingWindowIfNeeded();
        
        // 获取当前窗口计数器
        int idx = currentWindowIndex.get();
        int currentCount = windowCounters[idx].get();
        
        // 判断是否可以通过请求
        if (currentCount < maxRequestsPerWindow) {
            windowCounters[idx].incrementAndGet();
            updateStats();
            return true;
        } else {
            log.debug("接口 {} 触发限流，当前窗口请求数已达上限", interfaceName);
            updateStats();
            return false;
        }
    }
    
    private void slidingWindowIfNeeded() {
        long current = System.currentTimeMillis();
        long last = lastResetTime.get();
        
        // 计算经过的窗口数
        long elapsedWindows = (current - last) / WINDOW_SIZE_MS;
        
        if (elapsedWindows > 0) {
            // CAS更新上次重置时间
            if (lastResetTime.compareAndSet(last, last + elapsedWindows * WINDOW_SIZE_MS)) {
                // 滑动窗口，最多滑动WINDOW_COUNT个窗口
                int windowsToSlide = (int) Math.min(elapsedWindows, WINDOW_COUNT);
                
                int oldIdx = currentWindowIndex.get();
                for (int i = 1; i <= windowsToSlide; i++) {
                    int newIdx = (oldIdx + i) % WINDOW_COUNT;
                    windowCounters[newIdx].set(0); // 重置窗口
                }
                
                // 更新当前窗口索引
                currentWindowIndex.set((oldIdx + windowsToSlide) % WINDOW_COUNT);
            }
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