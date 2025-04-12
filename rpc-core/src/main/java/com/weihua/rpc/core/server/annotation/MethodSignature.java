/*
 * @Author: weihua hu
 * @Date: 2025-04-12 15:24:13
 * @LastEditTime: 2025-04-12 15:24:18
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.annotation;

import java.lang.reflect.Method;

/**
 * 方法签名工具类，用于统一生成和解析方法签名
 */
public final class MethodSignature {
    
    /**
     * 根据类和方法生成方法签名
     * 格式: 类名#方法名(参数类型1,参数类型2)
     * 示例: com.example.UserService#getUser(java.lang.String,int)
     */
    public static String generate(Class<?> clazz, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append('#').append(method.getName()).append('(');

        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(paramTypes[i].getName());
        }
        sb.append(')');

        return sb.toString();
    }
    
    /**
     * 将方法签名转换为Consul元数据友好格式
     * 格式: 类名.方法名_参数类型1_参数类型2
     */
    public static String toConsulFormat(String signature) {
        return signature.replace('#', '.')
                .replace("(", "_")
                .replace(")", "")
                .replace(",", "_")
                .replace(" ", "");
    }
    
    /**
     * 将方法签名转换为ZooKeeper节点友好格式
     * 格式: 类名-方法名(参数类型1,参数类型2)
     */
    public static String toZkFormat(String signature) {
        return signature.replace('#', '-');
    }
}
