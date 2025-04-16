/*
 * @Author: weihua hu
 * @Date: 2025-04-16 18:38:00
 * @LastEditTime: 2025-04-16 18:38:02
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.fallback;

import com.weihua.rpc.common.extension.SPI;
import com.weihua.rpc.core.server.annotation.RateLimit.FallbackStrategy;

import java.lang.reflect.Method;

/**
 * 限流降级处理器接口，支持SPI扩展
 */
@SPI("reject")
public interface RateLimitFallbackHandler {

    /**
     * 处理被限流的请求
     *
     * @param method 被调用的方法
     * @param args   方法参数
     * @param target 方法所属对象
     * @return 降级后的返回结果
     * @throws Throwable 处理过程中可能抛出的异常
     */
    Object handleRejectedRequest(Method method, Object[] args, Object target) throws Throwable;

    /**
     * 获取降级策略类型
     *
     * @return 降级策略类型
     */
    FallbackStrategy getStrategy();
}