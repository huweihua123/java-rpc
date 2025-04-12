/*
 * @Author: weihua hu
 * @Date: 2025-04-12 13:58:13
 * @LastEditTime: 2025-04-12 13:58:15
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 客户端模式条件
 * 当配置为client或hybrid时满足条件
 */
public class ClientModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("rpc.mode");
        return "client".equals(mode) || "hybrid".equals(mode);
    }
}
