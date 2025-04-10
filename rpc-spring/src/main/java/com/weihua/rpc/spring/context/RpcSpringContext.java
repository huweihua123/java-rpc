/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:33:13
 * @LastEditTime: 2025-04-10 02:33:14
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * RPC Spring上下文持有类
 * 提供静态方法访问Spring容器中的Bean
 */
@Component
public class RpcSpringContext implements ApplicationContextAware, DisposableBean {

    // Spring应用上下文
    private static ApplicationContext applicationContext;

    /**
     * 设置Spring上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (RpcSpringContext.applicationContext == null) {
            RpcSpringContext.applicationContext = applicationContext;
        }
    }

    /**
     * 获取Spring上下文
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 根据Bean名称获取Bean实例
     */
    public static Object getBean(String name) {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring上下文未初始化");
        }
        return applicationContext.getBean(name);
    }

    /**
     * 根据Bean类型获取Bean实例
     */
    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring上下文未初始化");
        }
        return applicationContext.getBean(clazz);
    }

    /**
     * 根据Bean名称和类型获取Bean实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring上下文未初始化");
        }
        return applicationContext.getBean(name, clazz);
    }

    /**
     * 销毁上下文
     */
    @Override
    public void destroy() {
        applicationContext = null;
    }
}
