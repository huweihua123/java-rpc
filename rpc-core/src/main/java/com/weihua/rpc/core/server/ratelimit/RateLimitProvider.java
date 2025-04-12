package com.weihua.rpc.core.server.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.weihua.rpc.core.condition.ConditionalOnServerMode;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 限流提供者
 * 管理各接口的限流器
 */
@Slf4j
@Component
//  @ConditionalOnProperty(name = "rpc.mode", havingValue = "server", matchIfMissing = false)
@ConditionalOnServerMode
public class RateLimitProvider {

    // 默认每个接口的最大QPS
    @Value("${rpc.ratelimit.default.qps:100}")
    private int defaultQps;

    // 是否启用限流
    @Value("${rpc.ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    // 限流器映射
    private final Map<String, RateLimit> rateLimits = new ConcurrentHashMap<>();

    // 统计监控调度器
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // 初始化监控定时任务
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-monitor");
            t.setDaemon(true);
            return t;
        });

        // 每分钟打印限流统计信息
        scheduler.scheduleAtFixedRate(
                this::printStats, 1, 1, TimeUnit.MINUTES);

        log.info("限流提供者初始化完成，默认QPS={}, 启用状态={}",
                defaultQps, rateLimitEnabled ? "开启" : "关闭");
    }

    /**
     * 获取接口的限流器
     * 
     * @param interfaceName 接口名称
     * @return 限流器实例
     */
    public RateLimit getRateLimit(String interfaceName) {
        // 如果禁用限流，返回无限制限流器
        if (!rateLimitEnabled) {
            return new UnlimitedRateLimit(interfaceName);
        }

        // 从缓存获取，如果不存在则创建
        return rateLimits.computeIfAbsent(interfaceName,
                key -> new TokenBucketRateLimit(key, getInterfaceQps(key)));
    }

    /**
     * 获取接口的QPS配置
     * 
     * @param interfaceName 接口名称
     * @return 配置的QPS，如果未配置则返回默认值
     */
    private int getInterfaceQps(String interfaceName) {
        // 这里可以从配置中心或Spring配置获取指定接口的QPS
        // 暂时采用默认QPS
        return defaultQps;
    }

    /**
     * 打印统计信息
     */
    private void printStats() {
        if (!rateLimitEnabled || rateLimits.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder("限流统计:\n");

        rateLimits.values().forEach(rateLimit -> {
            sb.append(String.format("接口: %-50s | 最大QPS: %5d | 当前QPS: %5.1f\n",
                    rateLimit.getInterfaceName(),
                    rateLimit.getMaxQps(),
                    rateLimit.getCurrentQps()));
        });

        log.info(sb.toString());
    }

    /**
     * 无限制的限流器实现，用于禁用限流时
     */
    private static class UnlimitedRateLimit implements RateLimit {
        private final String interfaceName;

        public UnlimitedRateLimit(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        @Override
        public boolean allowRequest() {
            return true;
        }

        @Override
        public String getInterfaceName() {
            return interfaceName;
        }

        @Override
        public int getMaxQps() {
            return Integer.MAX_VALUE;
        }

        @Override
        public double getCurrentQps() {
            return 0;
        }
    }
}
