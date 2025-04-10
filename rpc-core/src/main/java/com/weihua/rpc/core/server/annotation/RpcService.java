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
@Target({ ElementType.TYPE, ElementType.METHOD })
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
     * 是否可重试，标记方法是否幂等并可安全重试
     */
    boolean retryable() default false;
}
