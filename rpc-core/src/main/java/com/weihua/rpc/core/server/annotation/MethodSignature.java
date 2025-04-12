/*
 * @Author: weihua hu
 * @Date: 2025-04-12 15:24:13
 * @LastEditTime: 2025-04-12 20:48:28
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.annotation;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
     * 根据类名、方法名和参数类型数组生成方法签名
     * 格式: 类名#方法名(参数类型1,参数类型2)
     * 示例: com.example.UserService#getUser(java.lang.String,int)
     * 
     * @param className  完整类名
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @return 方法签名
     */
    public static String generate(String className, String methodName, Class<?>[] paramTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append('#').append(methodName).append('(');

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
     * 使用简单替换规则生成短键名，确保不超过Consul的128字符限制
     * 
     * @param signature 原始方法签名
     * @return Consul友好的键名
     */
    public static String toConsulFormat(String signature) {
        // 提取类简称
        String className = signature.substring(0, signature.indexOf('#'));
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);

        // 提取方法名
        String methodName = signature.substring(signature.indexOf('#') + 1, signature.indexOf('('));

        // 提取参数简称
        StringBuilder params = new StringBuilder();
        String paramsStr = signature.substring(signature.indexOf('(') + 1, signature.indexOf(')'));
        if (!paramsStr.isEmpty()) {
            String[] paramTypes = paramsStr.split(",");
            for (String paramType : paramTypes) {
                // 只保留简单类名
                String simpleType = paramType.substring(paramType.lastIndexOf('.') + 1);
                params.append(simpleType.substring(0, Math.min(3, simpleType.length()))).append('-');
            }
        }

        // 组合为短键名，格式: 类简称-方法名-参数简称
        String key = simpleClassName + "-" + methodName;
        if (params.length() > 0) {
            key += "-" + params.substring(0, params.length() - 1); // 移除最后一个分隔符
        }

        return key;
    }

    /**
     * 将方法签名转换为ZooKeeper节点友好格式
     * 格式: 类名-方法名(参数类型1,参数类型2)
     */
    public static String toZkFormat(String signature) {
        return signature.replace('#', '-');
    }

    /**
     * 生成简短的方法标识，主要用于日志和显示
     * 
     * @param signature 原始方法签名
     * @return 简短标识，如 "UserService.getUser"
     */
    public static String toShortName(String signature) {
        // 提取类名简称
        String className = signature.substring(0, signature.indexOf('#'));
        String shortClassName = className.substring(className.lastIndexOf('.') + 1);

        // 提取方法名
        String methodName = signature.substring(signature.indexOf('#') + 1, signature.indexOf('('));

        return shortClassName + "." + methodName;
    }
}