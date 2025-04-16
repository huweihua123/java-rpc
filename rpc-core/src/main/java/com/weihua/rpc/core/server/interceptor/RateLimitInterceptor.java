/*
 * @Author: weihua hu
 * @Date: 2025-04-16 18:40:26
 * @LastEditTime: 2025-04-16 18:40:28
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.interceptor;

import com.weihua.rpc.core.server.annotation.RateLimit;
import com.weihua.rpc.core.server.fallback.FallbackHandlerFactory;
import com.weihua.rpc.core.server.fallback.RateLimitFallbackHandler;
import com.weihua.rpc.core.server.ratelimit.RateLimitManager;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 限流拦截器，负责检查限流并执行降级策略
 */
@Slf4j
public class RateLimitInterceptor {

    private final RateLimitManager rateLimitManager;

    public RateLimitInterceptor(RateLimitManager rateLimitManager) {
        this.rateLimitManager = rateLimitManager;
    }

    /**
     * 处理方法调用
     * 
     * @param method       被调用的方法
     * @param args         方法参数
     * @param target       方法所属对象
     * @param methodKey    方法键
     * @param rateLimitAnn 限流注解
     * @return 方法执行结果或降级结果
     * @throws Throwable 执行过程中可能抛出的异常
     */
    public Object process(Method method, Object[] args, Object target,
            String methodKey, RateLimit rateLimitAnn) throws Throwable {

        // 如果注解禁用了限流，直接执行方法
        if (rateLimitAnn != null && !rateLimitAnn.enabled()) {
            return method.invoke(target, args);
        }

        // 执行限流检查
        boolean allowed = rateLimitManager.checkMethodRateLimit(methodKey);

        if (allowed) {
            // 限流通过，执行原方法
            return method.invoke(target, args);
        } else {
            // 限流不通过，执行降级策略
            log.debug("方法 {} 触发限流，执行降级策略", methodKey);

            // 获取降级策略
            RateLimit.FallbackStrategy fallbackStrategy = RateLimit.FallbackStrategy.REJECT;
            if (rateLimitAnn != null) {
                fallbackStrategy = rateLimitAnn.fallback();
            }

            // 获取对应的降级处理器
            RateLimitFallbackHandler fallbackHandler = FallbackHandlerFactory.getHandler(fallbackStrategy);

            // 执行降级处理
            return fallbackHandler.handleRejectedRequest(method, args, target);
        }
    }
}