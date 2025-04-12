/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:32:34
 * @LastEditTime: 2025-04-12 20:46:21
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.processor;

import com.weihua.rpc.core.server.annotation.MethodSignature;
import com.weihua.rpc.core.server.annotation.RateLimit;
import com.weihua.rpc.core.server.provider.ServiceProvider;
import com.weihua.rpc.spring.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC服务注解处理器
 * 处理带有@RpcService注解的Bean，将其注册到服务提供者
 */
@Slf4j
// @Component
public class RpcServiceBeanPostProcessor implements BeanPostProcessor {

    @Autowired
    private ServiceProvider serviceProvider;

    // 缓存处理过的服务接口注解
    private final Map<Class<?>, Map<String, Annotation>> interfaceAnnotationCache = new HashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 检查Bean是否带有@RpcService注解
        RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
        if (rpcService == null) {
            return bean;
        }

        log.info("发现RPC服务: {}", beanName);

        // 获取服务接口类
        Class<?> interfaceClass = rpcService.interfaceClass();
        if (interfaceClass == void.class) {
            // 如果未指定接口类，则获取类实现的所有接口
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces.length == 0) {
                throw new IllegalStateException("RPC服务必须实现至少一个接口或指定接口类: " + beanName);
            }
            interfaceClass = interfaces[0];
        }

        // 注册服务
        try {
            // 将服务注册到ServiceProvider
            serviceProvider.registerService(interfaceClass, bean);

            // 将服务接口上的@RpcService注解应用到具体方法
            applyServiceAnnotations(interfaceClass, bean.getClass());

            log.info("RPC服务注册成功: {}, 接口: {}", beanName, interfaceClass.getName());
        } catch (Exception e) {
            log.error("注册RPC服务失败: " + beanName, e);
            throw new BeansException("注册RPC服务失败: " + e.getMessage()) {
            };
        }

        return bean;
    }

    /**
     * 将服务接口上的注解信息应用到具体实现类中
     * 处理方法级别的重试、限流等注解
     */
    private void applyServiceAnnotations(Class<?> interfaceClass, Class<?> implClass) {
        // 获取或创建接口注解缓存
        Map<String, Annotation> methodAnnotations = interfaceAnnotationCache.computeIfAbsent(
                interfaceClass, k -> new HashMap<>());

        // 处理接口级别的注解
        processClassAnnotations(interfaceClass, implClass);

        // 处理接口方法级别的注解
        for (Method interfaceMethod : interfaceClass.getDeclaredMethods()) {
            try {
                Method implMethod = implClass.getMethod(
                        interfaceMethod.getName(), interfaceMethod.getParameterTypes());

                // 处理重试注解
                // processRetryAnnotation(interfaceMethod, implMethod);

                // 处理限流注解
                processRateLimitAnnotation(interfaceMethod, implMethod);

            } catch (NoSuchMethodException e) {
                log.warn("实现类{}中找不到接口方法: {}", implClass.getName(), interfaceMethod.getName());
            }
        }
    }

    /**
     * 处理类级别注解的继承
     */
    private void processClassAnnotations(Class<?> interfaceClass, Class<?> implClass) {
        // 处理RpcService注解
        com.weihua.rpc.core.server.annotation.RpcService interfaceRpcService = interfaceClass
                .getAnnotation(com.weihua.rpc.core.server.annotation.RpcService.class);

        if (interfaceRpcService != null) {
            log.debug("接口{}上存在@RpcService注解", interfaceClass.getName());

            // 检查实现类是否已有该注解
            com.weihua.rpc.core.server.annotation.RpcService implRpcService = implClass
                    .getAnnotation(com.weihua.rpc.core.server.annotation.RpcService.class);

            // 如果接口有注解但实现类没有，可以通过动态代理等方式应用接口的注解
            if (implRpcService == null) {
                log.debug("将接口的@RpcService注解属性应用到实现类: {}", implClass.getName());
                // 此处可以实现动态代理或元数据保存逻辑
            }
        }

        // 处理RateLimit注解
        RateLimit interfaceRateLimit = interfaceClass.getAnnotation(RateLimit.class);
        if (interfaceRateLimit != null) {
            log.debug("接口{}上存在@RateLimit注解, QPS={}",
                    interfaceClass.getName(), interfaceRateLimit.qps());

            // 检查实现类是否已有该注解
            RateLimit implRateLimit = implClass.getAnnotation(RateLimit.class);
            if (implRateLimit == null) {
                log.debug("将接口的@RateLimit注解属性应用到实现类: {}", implClass.getName());
                // 此处可以实现动态代理或元数据保存逻辑
            }
        }
    }

    /**
     * 处理方法级别限流注解的继承
     */
    private void processRateLimitAnnotation(Method interfaceMethod, Method implMethod) {
        // 获取接口方法上的RateLimit注解
        RateLimit interfaceAnnotation = AnnotationUtils.findAnnotation(interfaceMethod, RateLimit.class);

        // 获取实现方法上的RateLimit注解
        RateLimit implAnnotation = AnnotationUtils.findAnnotation(implMethod, RateLimit.class);

        // 使用实际有效的注解（优先使用实现类注解）
        RateLimit effectiveAnnotation = implAnnotation != null ? implAnnotation : interfaceAnnotation;

        // 如果存在有效的注解，注册到ServiceProvider的限流管理器
        if (effectiveAnnotation != null && effectiveAnnotation.enabled()) {
            // 获取方法签名
            String methodSignature = MethodSignature.generate(interfaceMethod.getDeclaringClass(), interfaceMethod);

            // 更新方法QPS限制
            serviceProvider.getRateLimitProvider().updateMethodQps(methodSignature, effectiveAnnotation.qps());

            // 检查是否明确指定了限流策略
            com.weihua.rpc.core.server.annotation.RateLimit.Strategy strategy = effectiveAnnotation.strategy();
            if (strategy != com.weihua.rpc.core.server.annotation.RateLimit.Strategy.DEFAULT) {
                // 如果注解中明确指定了策略，则使用注解中的策略覆盖默认策略
                serviceProvider.getRateLimitProvider().updateMethodStrategy(methodSignature, strategy);
                log.debug("为方法{}注册限流配置: {} QPS, 策略={} (注解指定)",
                        methodSignature, effectiveAnnotation.qps(), strategy);
            } else {
                // 使用默认策略，不做额外处理，由RateLimitProvider提供默认策略
                log.debug("为方法{}注册限流配置: {} QPS, 策略=默认",
                        methodSignature, effectiveAnnotation.qps());
            }
        }
        // 如果没有注解，什么都不做，表示该方法不需要限流
    }
}
