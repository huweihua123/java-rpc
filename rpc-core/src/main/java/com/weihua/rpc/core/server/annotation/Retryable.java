/*
 * @Author: weihua hu
 * @Date: 2025-04-12 16:06:21
 * @LastEditTime: 2025-04-12 16:06:36
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.annotation;

import java.lang.annotation.*;

/**
 * RPC方法重试注解
 * 标记一个方法是幂等的，可以安全重试
 * 仅适用于方法级别
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD }) // 仅限方法级别
public @interface Retryable {

    /**
     * 最大重试次数
     */
    int maxRetries() default 3;

    /**
     * 重试间隔(毫秒)
     */
    long retryInterval() default 1000;

    /**
     * 指数退避策略(是否每次重试增加间隔时间)
     */
    boolean backoff() default true;

    /**
     * 可重试的异常类型
     */
    Class<? extends Throwable>[] retryFor() default { Exception.class };

    /**
     * 不可重试的异常类型(优先级高于retryFor)
     */
    Class<? extends Throwable>[] noRetryFor() default {};

    /**
     * 描述信息（用于说明为什么方法可以安全重试）
     */
    String description() default "";
}
