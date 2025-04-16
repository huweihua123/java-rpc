package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.core.server.annotation.RateLimitStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流提供者，提供限流算法和限流检查
 */
@Slf4j
@Component
public class RateLimitProvider {

    // 配置信息
    private final RateLimitConfig config;

    // 方法维度的限流器缓存
    private final Map<String, AbstractRateLimiter> methodRateLimiters = new ConcurrentHashMap<>();
    // 方法限流策略缓存
    private final Map<String, RateLimitStrategy> methodStrategies = new ConcurrentHashMap<>();

    public RateLimitProvider(RateLimitConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("限流提供者初始化完成, 默认策略: {}", config.getDefaultStrategy());
    }

    /**
     * 获取方法限流器，如果不存在则创建
     */
    public AbstractRateLimiter getMethodRateLimit(String methodSignature) {
        return methodRateLimiters.computeIfAbsent(methodSignature, key -> {
            // 使用方法的特定策略或默认策略创建限流器
            RateLimitStrategy strategy = methodStrategies.getOrDefault(key, config.getDefaultStrategy());

            // 创建默认QPS的限流器
            AbstractRateLimiter limiter = createRateLimiter(strategy, config.getDefaultQps());
            log.debug("为方法{}创建限流器: 策略={}, 默认QPS={}",
                    key, strategy, config.getDefaultQps());
            return limiter;
        });
    }

    /**
     * 创建指定策略的限流器
     */
    private AbstractRateLimiter createRateLimiter(RateLimitStrategy strategy, int qps) {
        switch (strategy) {
            case SLIDING_WINDOW:
                return new SlidingWindowRateLimiter(qps);
            case TOKEN_BUCKET:
                return new TokenBucketRateLimiter(qps);
            case LEAKY_BUCKET:
                return new LeakyBucketRateLimiter(qps);
            default:
                log.warn("未知限流策略 {}, 使用令牌桶作为默认策略", strategy);
                return new TokenBucketRateLimiter(qps);
        }
    }

    /**
     * 更新方法的QPS限制
     */
    public void updateMethodQps(String methodSignature, int newQps) {
        // 获取现有限流器或创建新的
        AbstractRateLimiter limiter = getMethodRateLimit(methodSignature);
        // 更新QPS
        limiter.updateQps(newQps);
        log.debug("更新方法{}的QPS限制为: {}", methodSignature, newQps);
    }

    /**
     * 更新方法的限流策略
     */
    public void updateMethodStrategy(String methodSignature, RateLimitStrategy strategy) {
        // 保存方法策略
        methodStrategies.put(methodSignature, strategy);

        // 如果已经有限流器，需要替换为新策略的限流器
        if (methodRateLimiters.containsKey(methodSignature)) {
            AbstractRateLimiter oldLimiter = methodRateLimiters.get(methodSignature);
            int currentQps = oldLimiter.getMaxQps();

            // 创建新策略的限流器并替换
            AbstractRateLimiter newLimiter = createRateLimiter(strategy, currentQps);
            methodRateLimiters.put(methodSignature, newLimiter);
            log.info("为方法{}更新限流策略为: {}", methodSignature, strategy);
        }
    }

    /**
     * 检查方法是否配置了限流
     */
    public boolean isRateLimited(String methodSignature) {
        return methodRateLimiters.containsKey(methodSignature);
    }
}