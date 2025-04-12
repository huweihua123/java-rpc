package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.core.condition.ConditionalOnServerMode;
import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
@ConditionalOnServerMode
public class RateLimitProvider {

    // 默认每个接口的最大QPS
    @Value("${rpc.ratelimit.default.qps:100}")
    private int defaultQps;

    // 是否启用限流
    @Value("${rpc.ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    // 默认限流策略
    @Value("${rpc.ratelimit.default.strategy:TOKEN_BUCKET}")
    private Strategy defaultStrategy;

    // 限流器映射
    private final Map<String, RateLimit> rateLimits = new ConcurrentHashMap<>();

    // 接口级别QPS配置
    private final Map<String, Integer> interfaceQpsConfig = new ConcurrentHashMap<>();

    // 方法级别QPS配置
    private final Map<String, Integer> methodQpsConfig = new ConcurrentHashMap<>();

    // 方法级别限流策略配置
    private final Map<String, Strategy> methodStrategyConfig = new ConcurrentHashMap<>();

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

        log.info("限流提供者初始化完成，默认QPS={}, 默认策略={}, 启用状态={}",
                defaultQps, defaultStrategy, rateLimitEnabled ? "开启" : "关闭");
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
                key -> RateLimitFactory.createRateLimit(key, getInterfaceQps(key), defaultStrategy));
    }

    /**
     * 获取方法级别的限流器
     * 
     * @param methodSignature 方法签名
     * @return 限流器实例
     */
    public RateLimit getMethodRateLimit(String methodSignature) {
        // 如果禁用限流，返回无限制限流器
        if (!rateLimitEnabled) {
            return new UnlimitedRateLimit(methodSignature);
        }

        // 从缓存获取，如果不存在则创建
        return rateLimits.computeIfAbsent(methodSignature,
                key -> {
                    int qps = getMethodQps(key);
                    Strategy strategy = getMethodStrategy(key);
                    return RateLimitFactory.createRateLimit(key, qps, strategy);
                });
    }

    /**
     * 获取接口的QPS配置
     * 
     * @param interfaceName 接口名称
     * @return 配置的QPS，如果未配置则返回默认值
     */
    private int getInterfaceQps(String interfaceName) {
        // 优先从接口配置中获取QPS值
        return interfaceQpsConfig.getOrDefault(interfaceName, defaultQps);
    }

    /**
     * 获取方法的QPS配置
     * 
     * @param methodSignature 方法签名
     * @return 配置的QPS，如果未配置，尝试获取接口级别QPS，最后使用默认值
     */
    private int getMethodQps(String methodSignature) {
        // 尝试获取方法级别QPS
        if (methodQpsConfig.containsKey(methodSignature)) {
            return methodQpsConfig.get(methodSignature);
        }

        // 尝试获取接口级别QPS（从方法签名中提取接口名）
        String interfaceName = extractInterfaceFromMethod(methodSignature);
        if (interfaceQpsConfig.containsKey(interfaceName)) {
            return interfaceQpsConfig.get(interfaceName);
        }

        // 使用默认QPS
        return defaultQps;
    }

    /**
     * 获取方法的限流策略
     * 
     * @param methodSignature 方法签名
     * @return 配置的策略，如未配置则使用默认策略
     */
    private Strategy getMethodStrategy(String methodSignature) {
        return methodStrategyConfig.getOrDefault(methodSignature, defaultStrategy);
    }

    /**
     * 从方法签名中提取接口名
     */
    private String extractInterfaceFromMethod(String methodSignature) {
        // 方法签名格式: 包名.类名#方法名(参数类型列表)
        if (methodSignature.contains("#")) {
            return methodSignature.substring(0, methodSignature.indexOf('#'));
        }
        return methodSignature;
    }

    /**
     * 检查方法是否需要限流
     * 
     * @param methodSignature 方法签名
     * @return 是否需要限流
     */
    public boolean isRateLimited(String methodSignature) {
        // 如果全局禁用限流，返回false
        if (!rateLimitEnabled) {
            return false;
        }

        // 如果方法有明确的QPS配置，说明需要限流
        if (methodQpsConfig.containsKey(methodSignature)) {
            return true;
        }

        // 检查接口是否配置了限流
        String interfaceName = extractInterfaceFromMethod(methodSignature);
        return interfaceQpsConfig.containsKey(interfaceName);
    }

    /**
     * 更新接口级别QPS配置
     * 
     * @param interfaceName 接口名称
     * @param qps           QPS值
     */
    public void updateInterfaceQps(String interfaceName, int qps) {
        log.debug("更新接口QPS配置: {} -> {}", interfaceName, qps);
        interfaceQpsConfig.put(interfaceName, qps);

        // 如果限流器已创建，需要重新创建
        refreshRateLimit(interfaceName);
    }

    /**
     * 更新方法级别QPS配置
     * 
     * @param methodSignature 方法签名
     * @param qps             QPS值
     */
    public void updateMethodQps(String methodSignature, int qps) {
        log.debug("更新方法QPS配置: {} -> {}", methodSignature, qps);
        methodQpsConfig.put(methodSignature, qps);

        // 如果限流器已创建，需要重新创建
        refreshRateLimit(methodSignature);
    }

    /**
     * 更新方法的限流策略
     * 
     * @param methodSignature 方法签名
     * @param strategy        限流策略
     */
    public void updateMethodStrategy(String methodSignature, Strategy strategy) {
        log.debug("更新方法限流策略: {} -> {}", methodSignature, strategy);
        methodStrategyConfig.put(methodSignature, strategy);

        // 如果限流器已创建，需要重新创建
        refreshRateLimit(methodSignature);
    }

    /**
     * 刷新指定名称的限流器
     */
    private void refreshRateLimit(String name) {
        if (rateLimits.containsKey(name)) {
            // 移除旧的限流器，下次访问时会创建新的
            rateLimits.remove(name);
            log.debug("已移除限流器: {}, 下次访问时将重新创建", name);
        }
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
            sb.append(String.format("接口/方法: %-50s | 最大QPS: %5d | 当前QPS: %5.1f | 策略: %s\n",
                    rateLimit.getInterfaceName(),
                    rateLimit.getMaxQps(),
                    rateLimit.getCurrentQps(),
                    rateLimit.getClass().getSimpleName()));
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