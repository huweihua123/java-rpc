package com.weihua.server.serviceCenter.impl;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegCheck;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.weihua.annotation.Retryable;
import com.weihua.client.config.RegistryConfigManager;
import com.weihua.server.serviceCenter.ServiceRegister;
import common.config.ConfigRefreshManager;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class ConsulServiceRegisterImpl implements ServiceRegister, ConfigRefreshManager.ConfigurableComponent {
    private static final String RETRY_TAG = "Retryable";
    // 存储已注册的服务ID
    private final Map<String, String> registeredServices = new ConcurrentHashMap<>();
    private Consul consulClient;
    private AgentClient agentClient;

    // 配置参数
    private String consulHost;
    private int consulPort;
    private int readTimeout;
    private int connectTimeout;
    private int writeTimeout;
    private String checkInterval;
    private String checkTimeout;
    private String deregisterTime;

    // 配置管理器
    private final RegistryConfigManager configManager;

    public ConsulServiceRegisterImpl() {
        // 使用RegistryConfigManager代替ConfigurationManager
        this.configManager = RegistryConfigManager.getInstance();

        // 从配置管理器获取配置
        loadConfig();

        // 初始化Consul客户端
        initConsulClient();

        // 注册到配置刷新管理器
        ConfigRefreshManager.getInstance().register(this);
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        // 从配置管理器获取Consul配置
        this.consulHost = configManager.getConsulHost();
        this.consulPort = configManager.getConsulPort();
        this.readTimeout = configManager.getConsulTimeout();
        this.connectTimeout = configManager.getConsulConnectTimeout();
        this.writeTimeout = configManager.getConsulWriteTimeout();
        this.checkInterval = configManager.getConsulCheckInterval();
        this.checkTimeout = configManager.getConsulCheckTimeout();
        this.deregisterTime = configManager.getConsulDeregisterTime();

        log.info("Consul服务注册中心配置: {}:{}, 读取超时: {}ms, 连接超时: {}ms, 写入超时: {}ms, 检查间隔: {}, 检查超时: {}, 注销时间: {}",
                consulHost, consulPort, readTimeout, connectTimeout, writeTimeout, checkInterval, checkTimeout,
                deregisterTime);
    }

    /**
     * 初始化Consul客户端
     */
    private void initConsulClient() {
        try {
            this.consulClient = Consul.builder()
                    .withUrl(String.format("http://%s:%d", consulHost, consulPort))
                    .withReadTimeoutMillis(readTimeout)
                    .withConnectTimeoutMillis(connectTimeout)
                    .withWriteTimeoutMillis(writeTimeout)
                    .build();
            this.agentClient = consulClient.agentClient();
            log.info("Consul客户端初始化成功: {}:{}", consulHost, consulPort);
        } catch (Exception e) {
            log.error("Consul客户端初始化失败: {}:{}, 错误: {}", consulHost, consulPort, e.getMessage(), e);
            throw new RuntimeException("Consul客户端初始化失败", e);
        }
    }

    /**
     * 刷新配置 - 实现ConfigurableComponent接口
     */
    @Override
    public void refreshConfig() {
        log.info("开始刷新Consul服务注册中心配置...");

        // 保存当前配置用于对比
        String oldHost = this.consulHost;
        int oldPort = this.consulPort;
        int oldReadTimeout = this.readTimeout;
        int oldConnectTimeout = this.connectTimeout;
        int oldWriteTimeout = this.writeTimeout;

        // 重新加载配置
        loadConfig();

        // 检查是否需要重新创建客户端
        boolean needRecreate = !this.consulHost.equals(oldHost)
                || this.consulPort != oldPort
                || this.readTimeout != oldReadTimeout
                || this.connectTimeout != oldConnectTimeout
                || this.writeTimeout != oldWriteTimeout;

        if (needRecreate) {
            log.info("Consul连接配置已变更，重新创建客户端");
            initConsulClient();

            // 重新注册所有服务
            if (!registeredServices.isEmpty()) {
                log.info("重新注册已有服务，数量: {}", registeredServices.size());
                // 实际重新注册逻辑需要保存服务地址信息，这里只是示例
            }
        }

        log.info("Consul服务注册中心配置刷新完成");
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

            // 创建TCP健康检查 - 使用配置的参数
            Registration.RegCheck check = ImmutableRegCheck.builder()
                    .tcp(serviceAddress.getHostName() + ":" + serviceAddress.getPort())
                    .interval(checkInterval)
                    .timeout(checkTimeout)
                    .deregisterCriticalServiceAfter(deregisterTime)
                    .build();

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
    @Override
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