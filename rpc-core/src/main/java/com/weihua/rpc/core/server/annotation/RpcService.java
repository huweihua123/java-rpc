/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:20:42
 * @LastEditTime: 2025-04-10 02:20:44
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * RPC服务注解，标记一个类为RPC服务实现
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE }) // 只能用于类级别
@Component
public @interface RpcService {

    /**
     * 服务版本
     */
    String version() default "1.0.0";

    /**
     * 服务分组
     */
    String group() default "default";

    /**
     * 服务权重，用于负载均衡
     */
    int weight() default 100;

    /**
     * @deprecated 使用@Retryable注解替代，并且只能在方法上使用
     */
    @Deprecated
    boolean retryable() default false;
}
