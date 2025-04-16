/*
 * @Author: weihua hu
 * @Date: 2025-04-12 15:24:13
 * @LastEditTime: 2025-04-13 15:21:31
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.annotation;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 方法签名工具类，用于统一生成和解析方法签名
 */
public class MethodSignature {

    /**
     * 根据类和方法生成方法签名
     * 格式: 类全名#方法名(参数类型1,参数类型2)
     * 示例: com.example.UserService#getUser(java.lang.String,int)
     *
     * @param clazz  类对象
     * @param method 方法对象
     * @return 方法签名
     */
    public static String generate(Class<?> clazz, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append('#').append(method.getName()).append('(');

        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parameterTypes[i].getName());
        }
        sb.append(')');

        return sb.toString();
    }

    /**
     * 根据类名和方法名生成方法签名
     */
    public static String generate(String className, String methodName, Class<?>[] parameterTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append('#').append(methodName).append('(');

        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parameterTypes[i].getName());
        }
        sb.append(')');

        return sb.toString();
    }

    // /**
    // * 将方法签名转换为元数据友好的格式
    // * 例如: com.example.UserService#getUser(java.lang.String,int)
    // * 转换为: com.example.UserService.getUser_java.lang.String_int
    // *
    // * @param methodSignature 标准方法签名
    // * @return 元数据友好格式的方法签名
    // */
    // public static String normalizeMethodSignature(String methodSignature) {
    // return methodSignature
    // .replace('#', '.')
    // .replace("(", "_")
    // .replace(")", "")
    // .replace(",", "_")
    // .replace(" ", "");
    // }

    /**
     * 将方法签名转换为短小且安全的格式
     * 使用方法名前缀 + 哈希值组合，确保短小且唯一
     * 
     * @param methodSignature 标准方法签名
     * @return 短小且安全的方法签名，适用于各种服务注册中心
     */
    public static String normalizeMethodSignature(String methodSignature) {
        if (methodSignature == null || methodSignature.isEmpty()) {
            return "";
        }

        try {
            // 提取方法名作为前缀
            int hashPos = methodSignature.indexOf('#');
            int parenPos = methodSignature.indexOf('(');

            String prefix = "";
            if (hashPos >= 0 && parenPos > hashPos) {
                String methodName = methodSignature.substring(hashPos + 1, parenPos);
                prefix = methodName.length() > 8 ? methodName.substring(0, 8) : methodName;
            }

            // 计算SHA-256哈希值
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(methodSignature.getBytes(StandardCharsets.UTF_8));

            // 将哈希值转换为16进制字符串并取前12个字符
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", hashBytes[i]));
            }

            // 组合前缀和哈希值
            return prefix + "-" + sb.toString();

        } catch (NoSuchAlgorithmException e) {
            // 如果SHA-256不可用，回退到简单的哈希码
            return methodSignature.hashCode() + "";
        }
    }

    /**
     * @deprecated 使用更简单直观的 normalizeMethodSignature 方法代替
     *             将方法签名转换为简化的Consul友好格式（仅用于向后兼容）
     */
    @Deprecated
    public static String toConsulFormat(String methodSignature) {
        // 保留此方法以便向后兼容，但标记为过时
        return normalizeMethodSignature(methodSignature);
    }
}