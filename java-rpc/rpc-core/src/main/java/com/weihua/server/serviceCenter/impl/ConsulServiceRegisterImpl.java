package com.weihua.server.serviceCenter.impl;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegCheck;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.weihua.annotation.Retryable;
import com.weihua.server.serviceCenter.ServiceRegister;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class ConsulServiceRegisterImpl implements ServiceRegister {
    private static final String RETRY_TAG = "Retryable";
    // 存储已注册的服务ID
    private final Map<String, String> registeredServices = new ConcurrentHashMap<>();
    private Consul consulClient;
    private AgentClient agentClient;

    public ConsulServiceRegisterImpl() {
        // 初始化Consul客户端，连接到本地Consul代理 - 添加超时配置
        this.consulClient = Consul.builder().withUrl("http://localhost:8500").withReadTimeoutMillis(20000) // 设置读取超时为20秒
                .withConnectTimeoutMillis(10000) // 连接超时
                .withWriteTimeoutMillis(10000) // 写入超时
                .build();
        this.agentClient = consulClient.agentClient();
    }

    @Override
    public void register(Class<?> clazz, InetSocketAddress serviceAddress) {
        String serviceName = clazz.getName();
        String serviceId = serviceName + "-" + getServiceAddress(serviceAddress);

        try {
            // 获取可重试方法
            List<String> retryableMethods = getRetryableMethod(clazz);
            log.info("可重试的方法：{}", retryableMethods);

            // 创建元数据，存储可重试方法信息
            Map<String, String> meta = new HashMap<>();
            for (int i = 0; i < retryableMethods.size(); i++) {
                meta.put("retryable-" + i, retryableMethods.get(i));
            }

            // 创建TCP健康检查 - 更适合Netty服务
            Registration.RegCheck check = ImmutableRegCheck.builder()
                    .tcp(serviceAddress.getHostName() + ":" + serviceAddress.getPort())
                    .interval("10s")
                    .timeout("5s")
                    .deregisterCriticalServiceAfter("30s")
                    .build();
            // 创建注册信息
            Registration service = ImmutableRegistration.builder().id(serviceId).name(serviceName)
                    .address(serviceAddress.getHostName()).port(serviceAddress.getPort()).check(check).meta(meta)
                    .build();

            // 注册服务
            agentClient.register(service);

            // 将服务ID添加到已注册的服务集合中
            registeredServices.put(serviceId, serviceName);

            log.info("服务注册成功，服务名：{}，服务地址：{}", serviceName, getServiceAddress(serviceAddress));
        } catch (Exception e) {
            log.error("服务注册失败，服务名：{}，错误信息：{}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        // 注销所有服务
        for (String serviceId : registeredServices.keySet()) {
            try {
                agentClient.deregister(serviceId);
                log.info("服务注销成功，服务ID：{}", serviceId);
            } catch (Exception e) {
                log.error("服务注销失败，服务ID：{}，错误信息：{}", serviceId, e.getMessage(), e);
            }
        }
        registeredServices.clear();
    }

    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() + ":" + serverAddress.getPort();
    }

    @Override
    public String toString() {
        return "consul";
    }

    // 保持现有方法不变
    private List<String> getRetryableMethod(Class<?> clazz) {
        List<String> retryableMethods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Retryable.class)) {
                String methodSignature = getMethodSignature(clazz, method);
                retryableMethods.add(methodSignature);
            }
        }
        return retryableMethods;
    }

    private String getMethodSignature(Class<?> clazz, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append("#").append(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            sb.append(parameterTypes[i].getName());
            if (i < parameterTypes.length - 1) {
                sb.append(",");
            } else {
                sb.append(")");
            }
        }
        return sb.toString();
    }
}