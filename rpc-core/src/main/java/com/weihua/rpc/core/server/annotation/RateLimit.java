/*
 * @Author: weihua hu
 * @Date: 2025-04-12 15:24:03
 * @LastEditTime: 2025-04-12 15:24:08
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.annotation;

import java.lang.annotation.*;

/**
 * RPC服务限流注解，可用于类或方法级别
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RateLimit {
    
    /**
     * 最大QPS（每秒查询数），默认为100
     */
    int qps() default 100;
    
    /**
     * 限流策略，默认为令牌桶
     */
    Strategy strategy() default Strategy.TOKEN_BUCKET;
    
    /**
     * 限流降级处理策略
     */
    FallbackStrategy fallback() default FallbackStrategy.REJECT;
    
    /**
     * 是否启用，默认为true
     */
    boolean enabled() default true;
    
    /**
     * 限流策略枚举
     */
    enum Strategy {
        /**
         * 令牌桶算法
         */
        TOKEN_BUCKET,
        
        /**
         * 漏桶算法
         */
        LEAKY_BUCKET,
        
        /**
         * 滑动窗口
         */
        SLIDING_WINDOW,
        
        /**
         * 计数器
         */
        COUNTER
    }
    
    /**
     * 限流降级处理策略
     */
    enum FallbackStrategy {
        /**
         * 直接拒绝请求
         */
        REJECT,
        
        /**
         * 请求排队
         */
        QUEUE,
        
        /**
         * 返回默认值
         */
        RETURN_DEFAULT
    }
}
