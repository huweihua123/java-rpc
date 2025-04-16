/*
 * @Author: weihua hu
 * @Date: 2025-04-16 18:37:49
 * @LastEditTime: 2025-04-16 18:43:27
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.fallback;

import com.weihua.rpc.core.server.annotation.RateLimit.FallbackStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 降级处理器抽象基类
 */
@Slf4j
public abstract class AbstractFallbackHandler implements RateLimitFallbackHandler {

    @Getter
    protected final FallbackStrategy strategy;

    public AbstractFallbackHandler(FallbackStrategy strategy) {
        this.strategy = strategy;
        log.debug("创建降级处理器: 策略={}", strategy);
    }

    @Override
    public Object handleRejectedRequest(Method method, Object[] args, Object target) throws Throwable {
        if (log.isDebugEnabled()) {
            log.debug("执行降级处理: 策略={}, 方法={}.{}",
                    strategy, target.getClass().getSimpleName(), method.getName());
        }
        return doHandleRejectedRequest(method, args, target);
    }

    /**
     * 具体的降级处理逻辑实现
     */
    protected abstract Object doHandleRejectedRequest(Method method, Object[] args, Object target) throws Throwable;

    /**
     * 获取降级策略类型
     *
     * @return 降级策略类型
     */
    @Override
    public FallbackStrategy getStrategy() {
        return strategy;
    }
}