/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:31:45
 * @LastEditTime: 2025-04-10 02:31:46
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * RPC服务提供注解
 * 用于标记一个服务实现类，将其发布为RPC服务
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@Component
public @interface RpcService {

    /**
     * 服务接口类，默认使用类实现的第一个接口
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 服务版本号
     */
    String version() default "1.0.0";

    /**
     * 服务分组
     */
    String group() default "default";

    /**
     * 是否可重试（幂等方法）
     */
    boolean retryable() default false;
}
