package com.weihua.server.serviceCenter.impl;

import com.weihua.annotation.Retryable;
import com.weihua.server.serviceCenter.ServiceRegister;
import com.orbitz.consul.Consul;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ConsulServiceRegisterImpl implements ServiceRegister {
    private static final String RETRY_TAG = "Retryable";
    private Consul consulClient;
    private AgentClient agentClient;
    // 存储已注册的服务ID，用于发送心跳
    private final Map<String, String> registeredServices = new ConcurrentHashMap<>();
    // 用于发送心跳的定时执行器
    private final ScheduledExecutorService heartbeatExecutor;

    public ConsulServiceRegisterImpl() {
        // 初始化Consul客户端，连接到本地Consul代理 - 添加超时配置
        this.consulClient = Consul.builder()
                .withUrl("http://localhost:8500")
                .withReadTimeoutMillis(20000) // 设置读取超时为20秒
                .withConnectTimeoutMillis(10000) // 连接超时
                .withWriteTimeoutMillis(10000) // 写入超时
                .build();
        this.agentClient = consulClient.agentClient();

        // 初始化心跳执行器
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "consul-heartbeat-sender");
            t.setDaemon(true);
            return t;
        });

        // 启动心跳任务，每15秒发送一次心跳（TTL为30秒，一般设置为TTL的一半）
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, 0, 15, TimeUnit.SECONDS);
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

            // 创建服务检查 - 设置TTL为30秒
            Registration.RegCheck check = Registration.RegCheck.ttl(30L);

            // 创建注册信息
            Registration service = ImmutableRegistration.builder()
                    .id(serviceId)
                    .name(serviceName)
                    .address(serviceAddress.getHostName())
                    .port(serviceAddress.getPort())
                    .check(check)
                    .meta(meta)
                    .build();

            // 注册服务
            agentClient.register(service);

            // 将服务ID添加到已注册的服务集合中，用于后续心跳发送
            registeredServices.put(serviceId, serviceName);

            // 立即将服务标记为通过
            agentClient.pass(serviceId);

            log.info("服务注册成功，服务名：{}，服务地址：{}", serviceName, getServiceAddress(serviceAddress));
        } catch (Exception e) {
            log.error("服务注册失败，服务名：{}，错误信息：{}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * 定期发送心跳到Consul
     */
    private void sendHeartbeat() {
        if (registeredServices.isEmpty()) {
            return;
        }

        for (String serviceId : registeredServices.keySet()) {
            try {
                agentClient.pass(serviceId);
                log.debug("发送心跳成功，服务ID：{}", serviceId);
            } catch (Exception e) {
                log.warn("发送心跳失败，服务ID：{}，错误信息：{}", serviceId, e.getMessage());
            }
        }
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        heartbeatExecutor.shutdown();
        try {
            if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() + ":" + serverAddress.getPort();
    }

    @Override
    public String toString() {
        return "consul";
    }

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
