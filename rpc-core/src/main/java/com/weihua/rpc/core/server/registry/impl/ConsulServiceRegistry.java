package com.weihua.rpc.core.server.registry.impl;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.weihua.rpc.core.condition.ConditionalOnServerMode;
import com.weihua.rpc.core.server.annotation.MethodSignature;
import com.weihua.rpc.core.server.annotation.RateLimit;
import com.weihua.rpc.core.server.annotation.Retryable;
import com.weihua.rpc.core.server.annotation.RpcService;
import com.weihua.rpc.core.server.config.RegistryConfig;
import com.weihua.rpc.core.server.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consul服务注册实现
 */
@Slf4j
@Component("consulServiceRegistry")
// @ConditionalOnExpression("'${rpc.mode:server}'.equals('server') &&
// '${rpc.registry.type:local}'.equals('consul')")
@ConditionalOnServerMode
@ConditionalOnProperty(name = "rpc.registry.type", havingValue = "consul", matchIfMissing = false)
public class ConsulServiceRegistry implements ServiceRegistry {

    @Autowired
    private RegistryConfig registryConfig;

    private Consul consulClient;
    private final Map<String, String> registeredServices = new HashMap<>();

    @PostConstruct
    public void init() {
        initConsulClient();
    }

    /**
     * 初始化Consul客户端
     */
    private void initConsulClient() {
        try {
            String address = registryConfig.getAddress();
            String[] hostAndPort = address.split(":");
            String host = hostAndPort[0];
            int port = Integer.parseInt(hostAndPort[1]);

            // consulClient = Consul.builder()
            // .withHostAndPort(host, port)
            // .withConnectTimeoutMillis(registryConfig.getConnectTimeout())
            // .withReadTimeoutMillis(registryConfig.getTimeout())
            // .build();

            this.consulClient = Consul.builder()
                    .withUrl(String.format("http://%s:%d", host, port)) // 改用URL格式
                    .withConnectTimeoutMillis(registryConfig.getConnectTimeout())
                    .withReadTimeoutMillis(registryConfig.getTimeout())
                    .build();

            log.info("Consul客户端初始化成功, 地址: {}:{}", host, port);
        } catch (Exception e) {
            log.error("Consul客户端初始化失败", e);
            throw new RuntimeException("Consul客户端初始化失败", e);
        }
    }

    @Override
    public void register(Class<?> clazz, InetSocketAddress serviceAddress) {
        String serviceName = clazz.getName();
        String serviceKey = serviceName + "|" + getServiceAddress(serviceAddress);

        try {
            // 获取AgentClient
            AgentClient agentClient = consulClient.agentClient();

            // 构建服务实例ID
            String serviceId = serviceName + "-" + serviceAddress.getHostString() + "-" + serviceAddress.getPort();

            // 获取可重试方法
            List<String> retryableMethods = getRetryableMethod(clazz);

            // 获取限流配置
            // Map<String, Integer> rateLimitMethods = getRateLimitMethods(clazz);

            // 准备元数据
            Map<String, String> meta = new HashMap<>();
            meta.put("version", "1.0.0"); // 版本信息
            meta.put("interface", serviceName); // 接口名称
            meta.put("containerized", "true"); // 标记为容器化服务
            log.info("注册可重试方法: {}", retryableMethods);
//            log.info("注册限流方法: {}", rateLimitMethods);

            // 添加可重试方法信息到元数据
            for (String method : retryableMethods) {
                log.info("注册可重试方法: {}", method);
                String normalizedMethod = MethodSignature.toConsulFormat(method);
                meta.put("retryable-" + normalizedMethod, "true");
            }

            // // 添加限流方法信息到元数据
            // for (Map.Entry<String, Integer> entry : rateLimitMethods.entrySet()) {
            // String normalizedMethod = MethodSignature.toConsulFormat(entry.getKey());
            // meta.put("ratelimit-" + normalizedMethod, entry.getValue().toString());
            // }

            // 创建TCP健康检查 - 使用配置的参数
            Registration.RegCheck check = Registration.RegCheck.tcp(
                    serviceAddress.getHostString() + ":" + serviceAddress.getPort(),
                    registryConfig.getCheckInterval(),
                    registryConfig.getCheckTimeout(),
                    registryConfig.getDeregisterTime());

            // 构建服务注册信息
            Registration service = ImmutableRegistration.builder()
                    .id(serviceId)
                    .name(serviceName)
                    .address(serviceAddress.getHostString())
                    .port(serviceAddress.getPort())
                    .check(check) // 使用TCP健康检查
                    .meta(meta)
                    .build();

            // 注册服务
            agentClient.register(service);

            // 记录已注册服务
            registeredServices.put(serviceKey, serviceId);

            log.info("成功注册服务到Consul: {}, 实例ID: {}, 使用TCP健康检查", serviceName, serviceId);

        } catch (Exception e) {
            log.error("服务注册失败，服务名：{}, 错误信息：{}", serviceName, e.getMessage(), e);
            throw new RuntimeException("服务注册失败", e);
        }
    }

    /**
     * 启动TTL更新器
     * 
     * @param serviceId 服务ID
     */
    private void startTtlUpdater(String serviceId) {
        Thread ttlUpdater = new Thread(() -> {
            AgentClient agentClient = consulClient.agentClient();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 更新TTL状态
                    agentClient.pass(serviceId);
                    log.debug("更新服务TTL状态: {}", serviceId);
                    // 每5秒更新一次TTL (TTL为10秒)
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("更新服务TTL失败: {}", e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "consul-ttl-updater-" + serviceId);

        ttlUpdater.setDaemon(true);
        ttlUpdater.start();
        log.info("启动TTL更新线程: {}", ttlUpdater.getName());
    }

    /**
     * 获取可重试方法列表
     */
    private List<String> getRetryableMethod(Class<?> clazz) {
        List<String> retryableMethodSignatures = new ArrayList<>();

        // 扫描所有方法，不仅仅是接口声明的方法
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            // 检查方法是否有@Retryable注解
            Retryable retryableAnnotation = method.getAnnotation(Retryable.class);

            if (retryableAnnotation != null) {
                log.info("方法上存在@Retryable注解: {}", method.getName());
                String methodSignature = MethodSignature.generate(clazz, method);
                retryableMethodSignatures.add(methodSignature);
                log.info("标记可重试方法(@Retryable): {}, 最大重试次数: {}",
                        methodSignature, retryableAnnotation.maxRetries());
                // 留空白行以便检查日志

            } else {
                // 向后兼容: 检查方法上的旧RpcService注解
                RpcService methodRpcService = method.getAnnotation(RpcService.class);
                if (methodRpcService != null && methodRpcService.retryable()) {
                    String methodSignature = MethodSignature.generate(clazz, method);
                    retryableMethodSignatures.add(methodSignature);
                    log.warn("标记可重试方法(已废弃的RpcService.retryable): {}, 请改用@Retryable",
                            methodSignature);
                }
            }
        }

        return retryableMethodSignatures;
    }

    /**
     * 获取服务方法限流配置
     */
    private Map<String, Integer> getRateLimitMethods(Class<?> clazz) {
        Map<String, Integer> rateLimitMethods = new HashMap<>();

        // 检查类级别限流注解
        RateLimit classRateLimit = clazz.getAnnotation(RateLimit.class);
        int defaultQps = classRateLimit != null && classRateLimit.enabled() ? classRateLimit.qps() : -1;

        // 扫描所有方法
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            // 检查方法级别限流注解
            RateLimit methodRateLimit = method.getAnnotation(RateLimit.class);

            // 方法注解优先，其次是类注解
            if (methodRateLimit != null && methodRateLimit.enabled()) {
                String methodSignature = MethodSignature.generate(clazz, method);
                rateLimitMethods.put(methodSignature, methodRateLimit.qps());
                log.debug("方法级限流配置: {} = {} qps", methodSignature, methodRateLimit.qps());
            } else if (defaultQps > 0) {
                String methodSignature = MethodSignature.generate(clazz, method);
                rateLimitMethods.put(methodSignature, defaultQps);
                log.debug("类级限流配置应用于方法: {} = {} qps", methodSignature, defaultQps);
            }
        }

        return rateLimitMethods;
    }

    /**
     * 构建方法签名
     */
    private String getMethodSignature(Class<?> clazz, Method method) {
        return MethodSignature.generate(clazz, method);
    }

    /**
     * 获取服务地址字符串
     */
    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostString() + ":" + serverAddress.getPort();
    }

    @Override
    @PreDestroy
    public void shutdown() {
        if (consulClient != null) {
            AgentClient agentClient = consulClient.agentClient();

            // 注销所有服务
            for (String serviceId : registeredServices.values()) {
                try {
                    agentClient.deregister(serviceId);
                    log.info("注销服务: {}", serviceId);
                } catch (Exception e) {
                    log.error("注销服务异常: {}", serviceId, e);
                }
            }

            log.info("Consul客户端已关闭");
        }
    }

    /**
     * 刷新配置
     */
    public void refreshConfig() {
        log.info("刷新Consul服务注册配置");

        // 关闭旧Consul客户端和注销所有服务
        shutdown();

        // 重新初始化
        initConsulClient();

        // 重新注册服务
        reregisterServices();
    }

    /**
     * 重新注册所有服务
     */
    private void reregisterServices() {
        Map<String, String> oldServices = new HashMap<>(registeredServices);
        registeredServices.clear();

        oldServices.forEach((serviceKey, serviceId) -> {
            try {
                String[] parts = serviceKey.split("\\|");
                String className = parts[0];
                String address = parts[1];

                // 解析Class和地址
                Class<?> clazz = Class.forName(className);
                String[] addressParts = address.split(":");
                InetSocketAddress serviceAddress = new InetSocketAddress(
                        addressParts[0], Integer.parseInt(addressParts[1]));

                // 重新注册
                register(clazz, serviceAddress);

            } catch (ClassNotFoundException e) {
                log.error("重新注册服务失败, 找不到类: {}", serviceKey, e);
            }
        });
    }

    @Override
    public String toString() {
        return "Consul(" + registryConfig.getAddress() + ")";
    }
}
