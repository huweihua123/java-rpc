package com.weihua.rpc.core.server.fallback.impl;

import com.weihua.rpc.core.server.annotation.RateLimit.FallbackStrategy;
import com.weihua.rpc.core.server.fallback.AbstractFallbackHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 返回默认值的降级处理器
 */
@Slf4j
public class DefaultValueFallbackHandler extends AbstractFallbackHandler {
    
    // 缓存方法的默认返回值
    private final Map<Method, Object> defaultValuesCache = new ConcurrentHashMap<>();
    
    public DefaultValueFallbackHandler() {
        super(FallbackStrategy.RETURN_DEFAULT);
    }
    
    @Override
    protected Object doHandleRejectedRequest(Method method, Object[] args, Object target) {
        return defaultValuesCache.computeIfAbsent(method, this::createDefaultValue);
    }
    
    /**
     * 为指定方法创建默认返回值
     */
    private Object createDefaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        log.debug("为方法 {}.{} 创建默认返回值，返回类型: {}", 
                method.getDeclaringClass().getSimpleName(), method.getName(), returnType.getName());
        
        // 处理原始类型
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) return false;
            if (returnType == char.class) return '\0';
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0.0f;
            if (returnType == double.class) return 0.0d;
            if (returnType == void.class) return null;
        }
        
        // 处理数组
        if (returnType.isArray()) {
            return Array.newInstance(returnType.getComponentType(), 0);
        }
        
        // 处理常见接口类型的空集合
        if (List.class.isAssignableFrom(returnType)) {
            return Collections.emptyList();
        }
        if (Set.class.isAssignableFrom(returnType)) {
            return Collections.emptySet();
        }
        if (Map.class.isAssignableFrom(returnType)) {
            return Collections.emptyMap();
        }
        if (Collection.class.isAssignableFrom(returnType)) {
            return Collections.emptyList();
        }
        
        // 处理Optional
        if (returnType == Optional.class) {
            return Optional.empty();
        }
        
        // 处理基本类型的包装类
        if (returnType == Boolean.class) return Boolean.FALSE;
        if (returnType == Character.class) return '\0';
        if (returnType == Byte.class) return (byte) 0;
        if (returnType == Short.class) return (short) 0;
        if (returnType == Integer.class) return 0;
        if (returnType == Long.class) return 0L;
        if (returnType == Float.class) return 0.0f;
        if (returnType == Double.class) return 0.0d;
        
        // 处理String
        if (returnType == String.class) return "";
        
        // 尝试处理复杂泛型
        if (method.getGenericReturnType() instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) method.getGenericReturnType();
            Type[] typeArguments = type.getActualTypeArguments();
            
            if (log.isDebugEnabled()) {
                log.debug("处理泛型返回类型: {}, 泛型参数: {}", returnType.getName(), 
                        Arrays.stream(typeArguments).map(Type::getTypeName).toArray());
            }
            
            // 这里可以根据需要扩展更多泛型类型的处理
        }
        
        try {
            // 尝试创建一个空对象实例 (如果有默认构造函数)
            return returnType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("无法为类型 {} 创建默认实例，返回null: {}", returnType.getName(), e.getMessage());
            // 其他类型，返回null
            return null;
        }
    }
}