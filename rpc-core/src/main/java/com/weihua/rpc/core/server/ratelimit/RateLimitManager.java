package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.core.server.annotation.MethodSignature;
import com.weihua.rpc.core.server.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流管理器，用于管理服务的限流配置和状态
 */
@Slf4j
@Component
public class RateLimitManager {

    @Autowired
    private RateLimitProvider rateLimitProvider;

    // 方法维度的限流器缓存
    private final Map<String, RateLimit> methodRateLimits = new ConcurrentHashMap<>();
    private final Map<String, Integer> defaultQps = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("限流管理器初始化完成");
    }

    /**
     * 注册服务的限流配置
     *
     * @param serviceClass 服务类
     */
    public void registerService(Class<?> serviceClass) {
        // 获取类级别限流配置
        RateLimit classRateLimit = serviceClass.getAnnotation(RateLimit.class);
        int defaultQpsValue = (classRateLimit != null && classRateLimit.enabled()) ? classRateLimit.qps()
                : Integer.MAX_VALUE;

        // 记录服务默认QPS
        String serviceName = serviceClass.getName();
        defaultQps.put(serviceName, defaultQpsValue);

        if (classRateLimit != null && classRateLimit.enabled()) {
            log.info("为服务{}注册类级限流配置: {} QPS, 策略={}", serviceName,
                    defaultQpsValue, classRateLimit.strategy());
        }

        // 扫描方法级别限流配置
        for (Method method : serviceClass.getMethods()) {
            // 获取方法签名
            String methodSignature = MethodSignature.generate(serviceClass, method);

            // 检查方法级别限流注解
            RateLimit methodRateLimit = method.getAnnotation(RateLimit.class);
            if (methodRateLimit != null && methodRateLimit.enabled()) {
                // 保存方法限流配置
                methodRateLimits.put(methodSignature, methodRateLimit);

                // 注册方法QPS和策略
                rateLimitProvider.updateMethodQps(methodSignature, methodRateLimit.qps());
                rateLimitProvider.updateMethodStrategy(methodSignature, methodRateLimit.strategy());

                log.info("为方法{}注册限流器: {} QPS, 策略={}", methodSignature,
                        methodRateLimit.qps(), methodRateLimit.strategy());
            } else if (classRateLimit != null && classRateLimit.enabled()) {
                // 应用类级别限流配置到方法
                rateLimitProvider.updateMethodQps(methodSignature, defaultQpsValue);
                rateLimitProvider.updateMethodStrategy(methodSignature, classRateLimit.strategy());

                log.info("为方法{}应用类级限流配置: {} QPS, 策略={}", methodSignature,
                        defaultQpsValue, classRateLimit.strategy());
            }
        }
    }

    /**
     * 检查方法是否需要限流
     *
     * @param methodSignature 方法签名
     * @return 是否需要限流
     */
    public boolean isRateLimited(String methodSignature) {
        return rateLimitProvider.isRateLimited(methodSignature);
    }

    /**
     * 检查请求是否允许通过限流规则
     * 如果方法不需要限流，始终返回true
     *
     * @param methodSignature 方法签名
     * @return 是否允许请求
     */
    public boolean allowRequest(String methodSignature) {
        // 如果方法不需要限流，直接放行
        if (!isRateLimited(methodSignature)) {
            return true;
        }

        // 否则检查限流规则
        return rateLimitProvider.getMethodRateLimit(methodSignature).allowRequest();
    }

    /**
     * 动态更新方法的QPS限制
     *
     * @param methodSignature 方法签名
     * @param newQps          新的QPS限制
     */
    public void updateMethodQps(String methodSignature, int newQps) {
        if (newQps <= 0) {
            log.warn("无效的QPS值: {}, 方法: {}", newQps, methodSignature);
            return;
        }

        // 更新方法QPS
        rateLimitProvider.updateMethodQps(methodSignature, newQps);
        log.info("更新方法限流QPS: {}, 新值={}", methodSignature, newQps);
    }

    /**
     * 获取方法当前实际QPS
     */
    public double getCurrentMethodQps(String methodSignature) {
        return rateLimitProvider.getMethodRateLimit(methodSignature).getCurrentQps();
    }

    /**
     * 获取方法最大QPS限制
     */
    public int getMethodMaxQps(String methodSignature) {
        return rateLimitProvider.getMethodRateLimit(methodSignature).getMaxQps();
    }
}