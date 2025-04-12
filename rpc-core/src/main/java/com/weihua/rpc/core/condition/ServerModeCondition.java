/*
 * @Author: weihua hu
 * @Date: 2025-04-12 13:58:11
 * @LastEditTime: 2025-04-12 14:00:25
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 服务器模式条件
 * 当配置为server或hybrid时满足条件
 */
public class ServerModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("rpc.mode");
        return "server".equals(mode) || "hybrid".equals(mode);
    }
}
