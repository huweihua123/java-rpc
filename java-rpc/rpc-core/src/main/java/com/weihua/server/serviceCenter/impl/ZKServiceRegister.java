package com.weihua.server.serviceCenter.impl;

import com.weihua.annotation.Retryable;
import com.weihua.client.config.RegistryConfigManager;
import com.weihua.server.serviceCenter.ServiceRegister;

import common.config.ConfigRefreshManager;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Log4j2
public class ZKServiceRegister implements ServiceRegister, ConfigRefreshManager.ConfigurableComponent {
    // 存储已注册的服务路径，便于关闭时清理
    private final Map<String, String> registeredServices = new ConcurrentHashMap<>();
    private CuratorFramework client;

    // 配置管理器
    private final RegistryConfigManager configManager;

    // 配置参数
    private String zkAddress;
    private int sessionTimeout;
    private int connectionTimeout;
    private int baseSleepTime;
    private int maxRetries;
    private String rootPath;
    private static final String RETRY = "CanRetry";

    public ZKServiceRegister() {
        // 使用注册中心配置管理器
        this.configManager = RegistryConfigManager.getInstance();

        // 获取配置
        this.zkAddress = configManager.getZkAddress();
        this.sessionTimeout = configManager.getZkSessionTimeout();
        this.connectionTimeout = configManager.getZkConnectionTimeout();
        this.baseSleepTime = configManager.getZkBaseSleepTime();
        this.maxRetries = configManager.getZkMaxRetries();
        this.rootPath = configManager.getZkRootPath();

        log.info("初始化ZooKeeper服务注册中心: 地址={}, 会话超时={}ms, 连接超时={}ms, 重试参数={}ms/{}次",
                zkAddress, sessionTimeout, connectionTimeout, baseSleepTime, maxRetries);

        // 初始化ZooKeeper客户端
        initZKClient();
    }

    /**
     * 初始化ZooKeeper客户端
     */
    private void initZKClient() {
        try {
            RetryPolicy policy = new ExponentialBackoffRetry(baseSleepTime, maxRetries);
            this.client = CuratorFrameworkFactory.builder()
                    .connectString(zkAddress)
                    .sessionTimeoutMs(sessionTimeout)
                    .connectionTimeoutMs(connectionTimeout)
                    .retryPolicy(policy)
                    .namespace(rootPath)
                    .build();

            // 添加连接状态监听
            client.getConnectionStateListenable().addListener((client, state) -> {
                log.info("ZooKeeper连接状态变更: {}", state);
                if (state == ConnectionState.RECONNECTED) {
                    log.info("ZooKeeper重新连接，尝试恢复注册的服务");
                    reregisterServices();
                }
            });
            this.client.start();

            // 等待连接建立
            if (!client.blockUntilConnected(connectionTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("连接ZooKeeper超时");
            }

            log.info("ZooKeeper客户端连接成功");
        } catch (Exception e) {
            log.error("初始化ZooKeeper客户端失败: {}", e.getMessage(), e);
            throw new RuntimeException("初始化ZooKeeper客户端失败", e);
        }
    }

    /**
     * 重新注册所有服务
     */
    private void reregisterServices() {
        for (Map.Entry<String, String> entry : registeredServices.entrySet()) {
            try {
                String[] parts = entry.getKey().split("\\|");
                if (parts.length == 2) {
                    Class<?> serviceClass = Class.forName(parts[0]);
                    String[] addressParts = parts[1].split(":");
                    InetSocketAddress address = new InetSocketAddress(
                            addressParts[0], Integer.parseInt(addressParts[1]));
                    register(serviceClass, address);
                }
            } catch (Exception e) {
                log.error("重新注册服务失败: {}", entry.getKey(), e);
            }
        }
    }

    /**
     * 刷新配置
     */
    public void refreshConfig() {
        // 获取最新配置
        String newAddress = configManager.getZkAddress();
        int newSessionTimeout = configManager.getZkSessionTimeout();
        int newConnectionTimeout = configManager.getZkConnectionTimeout();
        int newBaseSleepTime = configManager.getZkBaseSleepTime();
        int newMaxRetries = configManager.getZkMaxRetries();
        String newRootPath = configManager.getZkRootPath();

        // 检查是否需要重新创建客户端
        boolean needRecreate = !newAddress.equals(zkAddress)
                || newSessionTimeout != sessionTimeout
                || newConnectionTimeout != connectionTimeout
                || newBaseSleepTime != baseSleepTime
                || newMaxRetries != maxRetries
                || !newRootPath.equals(rootPath);

        if (needRecreate) {
            log.info("ZooKeeper配置有变更，重新创建客户端");

            // 更新配置
            this.zkAddress = newAddress;
            this.sessionTimeout = newSessionTimeout;
            this.connectionTimeout = newConnectionTimeout;
            this.baseSleepTime = newBaseSleepTime;
            this.maxRetries = newMaxRetries;
            this.rootPath = newRootPath;

            // 关闭旧客户端
            if (client != null) {
                client.close();
            }

            // 重新初始化客户端
            initZKClient();

            // 重新注册服务
            for (Map.Entry<String, String> entry : registeredServices.entrySet()) {
                try {
                    String[] parts = entry.getKey().split("\\|");
                    if (parts.length == 2) {
                        Class<?> serviceClass = Class.forName(parts[0]);
                        String[] addressParts = parts[1].split(":");
                        InetSocketAddress address = new InetSocketAddress(
                                addressParts[0], Integer.parseInt(addressParts[1]));
                        register(serviceClass, address);
                    }
                } catch (Exception e) {
                    log.error("重新注册服务失败: {}", entry.getKey(), e);
                }
            }
        }

        log.info("ZooKeeper服务注册中心配置已刷新");
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
                log.info("服务地址 {} 已经存在，跳过注册", path);
            }

            // 记录已注册的服务
            registeredServices.put(serviceKey, path);

            // 注册可重试方法
            List<String> retryableMethods = getRetryableMethod(clazz);
            log.info("可重试的方法: {}", retryableMethods);

            // 使用单独的命名空间保存可重试方法信息
            CuratorFramework retryClient = client.usingNamespace(RETRY);
            String serviceAddressPath = getServiceAddress(serviceAddress);

            // 确保基路径存在
            if (retryClient.checkExists().forPath("/" + serviceAddressPath) == null) {
                retryClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT)
                        .forPath("/" + serviceAddressPath);
            }

            // 注册每个可重试方法
            for (String retryableMethod : retryableMethods) {
                String methodPath = "/" + serviceAddressPath + "/" + retryableMethod;
                if (retryClient.checkExists().forPath(methodPath) == null) {
                    retryClient.create().withMode(CreateMode.EPHEMERAL).forPath(methodPath);
                }
            }
        } catch (Exception e) {
            log.error("服务注册失败，服务名：{}，错误信息：{}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * 关闭ZooKeeper客户端
     */
    public void shutdown() {
        try {
            if (client != null) {
                // 清理所有临时节点不需要手动操作，ZK会自动清理
                log.info("关闭ZooKeeper客户端连接");
                client.close();
            }
        } catch (Exception e) {
            log.error("关闭ZooKeeper客户端失败", e);
        }
    }

    @Override
    public String toString() {
        return "zookeeper";
    }

    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() + ":" + serverAddress.getPort();
    }

    // 判断一个方法是否加了Retryable注解
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