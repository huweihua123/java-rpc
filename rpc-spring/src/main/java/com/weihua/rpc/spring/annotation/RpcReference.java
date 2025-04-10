/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:31:52
 * @LastEditTime: 2025-04-10 02:32:23
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.annotation;

import java.lang.annotation.*;

/**
 * RPC服务引用注解
 * 用于注入远程服务的代理对象
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface RpcReference {

    /**
     * 服务版本号
     */
    String version() default "1.0.0";

    /**
     * 服务分组
     */
    String group() default "default";

    /**
     * 服务调用超时时间（毫秒）
     */
    int timeout() default 3000;

    /**
     * 重试次数
     */
    int retries() default 2;

    /**
     * 是否启用熔断
     */
    boolean enableCircuitBreaker() default true;

    /**
     * 负载均衡策略
     * 可选值：random, roundrobin, leastactive, consistenthash
     */
    String loadBalance() default "random";
}
