/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:32:34
 * @LastEditTime: 2025-04-10 16:33:26
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.processor;

import com.weihua.rpc.core.server.provider.ServiceProvider;
import com.weihua.rpc.spring.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * RPC服务注解处理器
 * 处理带有@RpcService注解的Bean，将其注册到服务提供者
 */
@Slf4j
// @Component
public class RpcServiceBeanPostProcessor implements BeanPostProcessor {

    @Autowired
    private ServiceProvider serviceProvider;

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
        // 检查接口上是否有RpcService注解
        RpcService interfaceAnnotation = interfaceClass.getAnnotation(RpcService.class);
        if (interfaceAnnotation != null) {
            // 接口有注解，可以处理接口级别的配置
            log.debug("接口上存在@RpcService注解: {}", interfaceClass.getName());
        }

        // 未来可以在这里处理更复杂的注解传递逻辑
        // 例如方法级别的重试、限流等注解
    }
}
