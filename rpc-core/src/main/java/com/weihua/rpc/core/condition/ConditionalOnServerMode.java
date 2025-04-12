/*
 * @Author: weihua hu
 * @Date: 2025-04-12 13:58:20
 * @LastEditTime: 2025-04-12 13:58:22
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * 条件注解：当RPC模式为server或hybrid时
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ServerModeCondition.class)
public @interface ConditionalOnServerMode {
}
