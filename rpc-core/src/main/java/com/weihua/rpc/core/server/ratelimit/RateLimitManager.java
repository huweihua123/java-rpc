package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import com.weihua.rpc.core.server.config.RateLimitConfig;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流管理器，专注于配置管理和高层次的限流决策
 */
@Slf4j
public class RateLimitManager {

    private final RateLimitConfig config;
    private final RateLimitProvider provider;

    /**
     * 用于定时任务的线程池
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 获取默认限流策略
     * 
     * @return 默认限流策略
     */
    public Strategy getDefaultStrategy() {
        return config.getDefaultStrategy();
    }

    /**
     * 统计重置周期（毫秒）
     */
    private static final long STATS_RESET_PERIOD_MS = 60_000; // 1分钟

    public RateLimitManager(RateLimitConfig config) {
        this.config = config;
        this.provider = new RateLimitProvider(config);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("ratelimit-scheduler-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };

        this.scheduler = new ScheduledThreadPoolExecutor(1, threadFactory);

        // 启动统计重置定时任务
        scheduler.scheduleAtFixedRate(
                this::resetAllStatistics,
                STATS_RESET_PERIOD_MS,
                STATS_RESET_PERIOD_MS,
                TimeUnit.MILLISECONDS);
    }

    @PostConstruct
    public void init() {
        log.info("限流管理器初始化完成: 启用状态={}", config.isEnabled() ? "启用" : "禁用");
    }

    /**
     * 检查方法级限流
     * 
     * @param methodKey 方法全限定名
     * @return 是否允许通过
     */
    public boolean checkMethodRateLimit(String methodKey) {
        if (!config.isEnabled()) {
            return true;
        }

        return provider.getMethodLimiter(methodKey).tryAcquire();
    }

    /**
     * 检查接口级限流
     * 
     * @param interfaceKey 接口全限定名
     * @return 是否允许通过
     */
    public boolean checkInterfaceRateLimit(String interfaceKey) {
        if (!config.isEnabled()) {
            return true;
        }

        return provider.getInterfaceLimiter(interfaceKey).tryAcquire();
    }

    /**
     * 检查服务级限流
     * 
     * @return 是否允许通过
     */
    public boolean checkServiceRateLimit() {
        if (!config.isEnabled()) {
            return true;
        }

        return provider.getServiceLimiter().tryAcquire();
    }

    /**
     * 检查IP级限流
     * 
     * @param ip 客户端IP地址
     * @return 是否允许通过
     */
    public boolean checkIpRateLimit(String ip) {
        if (!config.isEnabled()) {
            return true;
        }

        return provider.getIpLimiter(ip).tryAcquire();
    }

    /**
     * 配置方法限流参数
     * 
     * @param methodKey 方法全限定名
     * @param qps       QPS限制
     * @param strategy  限流策略
     */
    public void configureMethodRateLimit(String methodKey, int qps, Strategy strategy) {
        config.getMethodQpsConfig().put(methodKey, qps);
        config.getMethodStrategyConfig().put(methodKey, strategy);

        log.info("设置方法限流配置: 方法={}, QPS={}, 策略={}", methodKey, qps, strategy);
    }

    /**
     * 配置接口限流参数
     * 
     * @param interfaceKey 接口全限定名
     * @param qps          QPS限制
     * @param strategy     限流策略
     */
    public void configureInterfaceRateLimit(String interfaceKey, int qps, Strategy strategy) {
        config.getInterfaceQpsConfig().put(interfaceKey, qps);
        config.getInterfaceStrategyConfig().put(interfaceKey, strategy);

        log.info("设置接口限流配置: 接口={}, QPS={}, 策略={}", interfaceKey, qps, strategy);
    }

    /**
     * 重置所有统计数据
     */
    private void resetAllStatistics() {
        if (log.isDebugEnabled()) {
            log.debug("定时重置所有限流器统计数据");
        }

        // 实际的重置操作（如果需要的话）
    }

    /**
     * 关闭限流管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        provider.clearAll();
        log.info("限流管理器已关闭");
    }
}