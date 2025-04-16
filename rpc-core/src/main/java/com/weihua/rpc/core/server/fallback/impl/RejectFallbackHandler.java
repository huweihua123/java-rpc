/*
 * @Author: weihua hu
 * @Date: 2025-04-16 18:38:43
 * @LastEditTime: 2025-04-16 18:38:45
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.fallback.impl;

import com.weihua.rpc.common.exception.RateLimitException;
import com.weihua.rpc.core.server.annotation.RateLimit.FallbackStrategy;
import com.weihua.rpc.core.server.fallback.AbstractFallbackHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 直接拒绝请求的降级处理器
 */
@Slf4j
public class RejectFallbackHandler extends AbstractFallbackHandler {

    public RejectFallbackHandler() {
        super(FallbackStrategy.REJECT);
    }

    @Override
    protected Object doHandleRejectedRequest(Method method, Object[] args, Object target) {
        String resourceName = target.getClass().getName() + "#" + method.getName();
        log.warn("请求被限流拒绝: {}", resourceName);
        throw new RateLimitException("Rate limit exceeded for resource: " + resourceName);
    }
}