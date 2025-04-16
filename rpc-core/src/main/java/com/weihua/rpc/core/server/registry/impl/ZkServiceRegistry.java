package com.weihua.rpc.core.server.registry.impl;

import com.weihua.rpc.core.server.annotation.MethodSignature;
import com.weihua.rpc.core.server.annotation.Retryable;
import com.weihua.rpc.core.server.annotation.RpcService;
import com.weihua.rpc.core.server.registry.AbstractServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ZooKeeper服务注册实现
 */
@Slf4j
public class ZkServiceRegistry extends AbstractServiceRegistry {

    private CuratorFramework client;
    private final Map<String, String> registeredServices = new HashMap<>();

    @Override
    public void init() {
        initZkClient();
    }

    /**
     * 初始化ZooKeeper客户端
     */
    private void initZkClient() {
        try {
            String zkAddress = registryConfig.getAddress();
            int connectTimeout = registryConfig.getConnectTimeout();
            int sessionTimeout = registryConfig.getTimeout();

            // 创建重试策略
            int baseSleepTimeMs = 1000;
            int maxRetries = 3;
            ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(baseSleepTimeMs, maxRetries);

            // 创建ZK客户端
            client = CuratorFrameworkFactory.builder()
                    .connectString(zkAddress)
                    .sessionTimeoutMs(sessionTimeout)
                    .connectionTimeoutMs(connectTimeout)
                    .retryPolicy(retryPolicy)
                    .namespace("rpc")
                    .build();

            // 启动客户端
            client.start();
            client.blockUntilConnected();
            log.info("ZooKeeper客户端初始化成功，地址: {}", zkAddress);
        } catch (Exception e) {
            log.error("ZooKeeper客户端初始化失败", e);
            throw new RuntimeException("ZooKeeper客户端初始化失败", e);
        }
    }

    @Override
    public void register(Class<?> clazz, InetSocketAddress serviceAddress) {
        String serviceName = clazz.getName();
        String serviceKey = serviceName + "|" + getServiceAddress(serviceAddress);

        try {
            // 创建服务节点（持久节点）
            if (client.checkExists().forPath("/" + serviceName) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath("/" + serviceName);
                log.info("服务节点 {} 创建成功", "/" + serviceName);
            }

            // 创建服务实例节点（临时节点）
            String path = "/" + serviceName + "/" + getServiceAddress(serviceAddress);
            if (client.checkExists().forPath(path) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);
                log.info("服务地址 {} 注册成功", path);
            } else {
                log.info("服务地址 {} 已存在", path);
            }

            // 记录已注册的服务
            registeredServices.put(serviceKey, path);

            // 注册可重试方法
            registerRetryableMethods(clazz, serviceAddress);

        } catch (Exception e) {
            log.error("注册服务失败: {}", serviceName, e);
            throw new RuntimeException("注册服务失败", e);
        }
    }

    /**
     * 注册可重试方法
     */
    private void registerRetryableMethods(Class<?> clazz, InetSocketAddress serviceAddress) {
        List<String> retryableMethods = getRetryableMethod(clazz);
        String servicePath = "/" + clazz.getName() + "/" + getServiceAddress(serviceAddress);

        for (String methodSignature : retryableMethods) {
            try {
                // 创建方法标识节点 - 使用统一的方法签名格式转换
                String methodPath = servicePath + "/methods/" + MethodSignature.toConsulFormat(methodSignature);
                if (client.checkExists().forPath(methodPath) == null) {
                    client.create().creatingParentsIfNeeded().forPath(methodPath, "true".getBytes());
                    log.info("注册可重试方法: {}", methodPath);
                }
            } catch (Exception e) {
                log.error("注册可重试方法异常: {}", methodSignature, e);
            }
        }
    }

    /**
     * 获取可重试方法列表
     */
    private List<String> getRetryableMethod(Class<?> clazz) {
        List<String> retryableMethodSignatures = new ArrayList<>();

        // 扫描所有方法，检查@Retryable注解
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            // 检查方法是否有@Retryable注解
            Retryable retryableAnnotation = method.getAnnotation(Retryable.class);

            // 向后兼容：检查方法上的RpcService注解(已废弃)
            RpcService methodRpcService = method.getAnnotation(RpcService.class);
            boolean isRetryable = (retryableAnnotation != null) ||
                    (methodRpcService != null && methodRpcService.retryable());

            if (isRetryable) {
                String methodSignature = MethodSignature.generate(clazz, method);
                retryableMethodSignatures.add(methodSignature);

                if (retryableAnnotation != null) {
                    log.info("标记可重试方法(@Retryable): {}, 最大重试次数: {}",
                            methodSignature, retryableAnnotation.maxRetries());
                } else {
                    log.warn("标记可重试方法(已废弃的@RpcService.retryable): {}, 请改用@Retryable",
                            methodSignature);
                }
            }
        }

        return retryableMethodSignatures;
    }

    /**
     * 重新注册所有服务
     */
    private void reregisterServices() {
        registeredServices.forEach((serviceKey, path) -> {
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

    /**
     * 获取服务地址字符串
     */
    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostString() + ":" + serverAddress.getPort();
    }

    @Override
    public void shutdown() {
        if (client != null) {
            // 删除注册的临时节点
            for (String path : registeredServices.values()) {
                try {
                    if (client.checkExists().forPath(path) != null) {
                        client.delete().forPath(path);
                        log.info("删除注册节点: {}", path);
                    }
                } catch (Exception e) {
                    log.error("删除注册节点异常: {}", path, e);
                }
            }

            // 关闭客户端
            client.close();
            log.info("ZooKeeper客户端已关闭");
        }
    }

    @Override
    public String toString() {
        return "ZooKeeper(" + registryConfig.getAddress() + ")";
    }
}