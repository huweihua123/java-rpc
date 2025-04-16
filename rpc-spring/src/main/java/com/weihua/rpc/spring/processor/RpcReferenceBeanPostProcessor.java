/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:32:51
 * @LastEditTime: 2025-04-10 16:35:01
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.processor;

import com.weihua.rpc.core.client.proxy.ClientProxyFactory;
import com.weihua.rpc.spring.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC引用注解处理器
 * 处理带有@RpcReference注解的字段，注入RPC代理对象
 */
@Slf4j
public class RpcReferenceBeanPostProcessor implements BeanPostProcessor {

    @Autowired
    private ClientProxyFactory clientProxyFactory;

    // 缓存已创建的代理对象，避免重复创建
    private final Map<String, Object> proxyCache = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 处理字段注解
        processFields(bean, beanName);

        // 处理方法注解（如Setter方法）
        processMethods(bean, beanName);

        return bean;
    }

    /**
     * 处理字段上的@RpcReference注解
     */
    private void processFields(Object bean, String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            RpcReference annotation = field.getAnnotation(RpcReference.class);
            if (annotation != null) {
                // 设置字段可访问
                field.setAccessible(true);

                // 获取或创建RPC代理对象
                Object proxy = getOrCreateProxy(field.getType(), annotation);

                try {
                    // 注入代理对象
                    field.set(bean, proxy);
                    log.info("为Bean[{}]的字段[{}]注入RPC代理", beanName, field.getName());
                } catch (IllegalAccessException e) {
                    log.error("注入RPC代理失败", e);
                }
            }
        });
    }

    /**
     * 处理方法上的@RpcReference注解（如Setter方法）
     */
    private void processMethods(Object bean, String beanName) {
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            RpcReference annotation = AnnotationUtils.findAnnotation(method, RpcReference.class);
            if (annotation != null && method.getName().startsWith("set") && method.getParameterCount() == 1) {
                // 获取需要注入的接口类型
                Class<?> interfaceType = method.getParameterTypes()[0];

                // 获取或创建RPC代理对象
                Object proxy = getOrCreateProxy(interfaceType, annotation);

                try {
                    // 调用Setter方法注入代理对象
                    method.invoke(bean, proxy);
                    log.info("通过方法[{}]为Bean[{}]注入RPC代理", method.getName(), beanName);
                } catch (Exception e) {
                    log.error("通过方法注入RPC代理失败", e);
                }
            }
        });
    }

    /**
     * 获取或创建RPC代理对象
     */
    private Object getOrCreateProxy(Class<?> interfaceType, RpcReference annotation) {
        // 生成唯一键，包括接口类型和版本、分组信息
        String proxyKey = interfaceType.getName() + ":" + annotation.version() + ":" + annotation.group();

        // 检查缓存
        return proxyCache.computeIfAbsent(proxyKey, key -> {
            log.info("创建RPC代理: {}, 版本: {}, 分组: {}, 超时: {}ms",
                    interfaceType.getName(), annotation.version(),
                    annotation.group(), annotation.timeout());

            // 调用ClientProxyFactory创建代理
            return clientProxyFactory.getProxy(interfaceType, annotation.version(), annotation.group());
        });
    }
}
