package com.weihua.rpc.core.client.registry.impl;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.config.DiscoveryConfig;
import com.weihua.rpc.core.client.invoker.Invoker;
import com.weihua.rpc.core.client.invoker.InvokerFactory;
import com.weihua.rpc.core.client.registry.ServiceCenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Consul服务发现中心实现
 */
@Slf4j
@Component
@ConditionalOnExpression("'${rpc.mode:server}'.equals('client') && '${rpc.registry.type:consul}'.equals('consul')")
public class ConsulServiceCenter implements ServiceCenter {

    // 服务缓存
    private final Map<String, List<String>> serviceCache = new ConcurrentHashMap<>();
    // 可重试方法缓存
    private final Map<String, Boolean> retryableMethodCache = new ConcurrentHashMap<>();
    // 地址变更监听器
    private final Map<String, Set<Consumer<List<String>>>> addressChangeListeners = new ConcurrentHashMap<>();
    @Autowired
    private DiscoveryConfig registryConfig;
    @Autowired
    private InvokerFactory invokerFactory;
    
    private Consul consulClient;
    // 定时同步任务
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // 初始化Consul客户端
        try {
            String address = registryConfig.getAddress();
            String host = address.split(":")[0];
            int port = Integer.parseInt(address.split(":")[1]);
            this.consulClient = Consul.builder().withUrl(String.format("http://%s:%d", host, port)) // 改用URL格式
                    .withConnectTimeoutMillis(registryConfig.getConnectTimeout())
                    .withReadTimeoutMillis(registryConfig.getTimeout())
                    .build();

            log.info("Consul客户端初始化成功: {}:{}", host, port);
        } catch (Exception e) {
            log.error("初始化Consul客户端失败: {}", e.getMessage(), e);
            throw new RuntimeException("初始化Consul客户端失败", e);
        }

        // 初始化定时同步任务
        initScheduler();
    }

    /**
     * 初始化调度器
     */
    private void initScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "consul-sync");
            t.setDaemon(true);
            return t;
        });

        // 定期同步已订阅的服务
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 同步所有已订阅的服务
                Set<String> services = new HashSet<>(addressChangeListeners.keySet());
                for (String service : services) {
                    syncServiceFromConsul(service);
                }
            } catch (Exception e) {
                log.error("定时同步服务失败: {}", e.getMessage(), e);
            }
        }, 0, registryConfig.getSyncPeriod(), TimeUnit.SECONDS);
    }

    /**
     * 同步指定服务
     */
    private void syncServiceFromConsul(String serviceName) {
        try {
            // 查询健康的服务实例
            HealthClient healthClient = consulClient.healthClient();
            List<ServiceHealth> services = healthClient.getHealthyServiceInstances(serviceName).getResponse();

            List<String> addresses = new ArrayList<>();
            for (ServiceHealth service : services) {
                String address = service.getService().getAddress();
                int port = service.getService().getPort();

                // 如果地址为空，使用节点地址
                if (address == null || address.isEmpty()) {
                    address = service.getNode().getAddress();
                }

                addresses.add(address + ":" + port);
            }

            // 更新缓存
            serviceCache.put(serviceName, addresses);

            // 通知变更
            notifyAddressChange(serviceName, addresses);

            log.debug("已同步服务 {} 实例列表，共 {} 个实例", serviceName, addresses.size());
        } catch (Exception e) {
            log.error("同步服务 {} 失败: {}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * 通知地址变更
     */
    private void notifyAddressChange(String serviceName, List<String> addresses) {
        Set<Consumer<List<String>>> listeners = addressChangeListeners.get(serviceName);
        if (listeners != null) {
            for (Consumer<List<String>> listener : listeners) {
                try {
                    listener.accept(addresses);
                } catch (Exception e) {
                    log.error("通知地址变更失败: {}", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public List<Invoker> discoverInvokers(RpcRequest request) {
        String serviceName = request.getInterfaceName();

        // 获取服务地址列表
        List<String> addresses = serviceCache.get(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            // 如果缓存中没有，尝试同步
            syncServiceFromConsul(serviceName);
            addresses = serviceCache.get(serviceName);

            if (addresses == null || addresses.isEmpty()) {
                log.warn("未发现服务: {}", serviceName);
                return Collections.emptyList();
            }
        }

        // 将地址转换为InetSocketAddress
        List<InetSocketAddress> socketAddresses = addresses.stream().map(this::parseAddress).filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 更新InvokerFactory中的服务地址
        invokerFactory.updateServiceAddresses(serviceName, socketAddresses);

        // 获取Invoker列表
        return invokerFactory.getInvokers(serviceName, socketAddresses);
    }

    /**
     * 解析地址字符串
     */
    private InetSocketAddress parseAddress(String address) {
        try {
            String[] parts = address.split(":");
            return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
        } catch (Exception e) {
            log.error("解析地址失败: {}", address, e);
            return null;
        }
    }

    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest request) {
        String serviceName = request.getInterfaceName();

        // 获取服务地址列表
        List<String> addresses = serviceCache.get(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            // 如果缓存中没有，尝试同步
            syncServiceFromConsul(serviceName);
            addresses = serviceCache.get(serviceName);

            if (addresses == null || addresses.isEmpty()) {
                log.warn("未发现服务: {}", serviceName);
                return null;
            }
        }

        // 简单的随机选择一个地址
        String address = addresses.get(ThreadLocalRandom.current().nextInt(addresses.size()));
        return parseAddress(address);
    }

    @Override
    public boolean isMethodRetryable(String methodSignature) {
        // 从缓存查询是否可重试
        return retryableMethodCache.computeIfAbsent(methodSignature, k -> checkMethodRetryable(methodSignature));
    }

    /**
     * 检查方法是否可重试
     */
    private boolean checkMethodRetryable(String methodSignature) {
        try {
            // 从方法签名提取服务名称
            String serviceName = extractServiceName(methodSignature);

            // 从Consul元数据查询可重试方法
            HealthClient healthClient = consulClient.healthClient();
            List<ServiceHealth> services = healthClient.getHealthyServiceInstances(serviceName).getResponse();

            if (!services.isEmpty()) {
                ServiceHealth service = services.get(0);
                Map<String, String> meta = service.getService().getMeta();

                // 检查元数据中是否标记该方法为可重试
                String retryableKey = "retryable-" + normalizeMethodSignature(methodSignature);
                return "true".equalsIgnoreCase(meta.getOrDefault(retryableKey, "false"));
            }
        } catch (Exception e) {
            log.error("检查方法是否可重试失败: {}", methodSignature, e);
        }

        return false;
    }

    /**
     * 从方法签名提取服务名称
     */
    private String extractServiceName(String methodSignature) {
        int hashIndex = methodSignature.indexOf('#');
        return hashIndex > 0 ? methodSignature.substring(0, hashIndex) : methodSignature;
    }

    /**
     * 规范化方法签名，以适应元数据键名
     */
    private String normalizeMethodSignature(String methodSignature) {
        return methodSignature.replace('#', '.').replace("(", "_").replace(")", "").replace(",", "_").replace(" ", "");
    }

    @Override
    public void subscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName != null && listener != null) {
            // 添加监听器
            Set<Consumer<List<String>>> listeners = addressChangeListeners.computeIfAbsent(serviceName,
                    k -> ConcurrentHashMap.newKeySet());
            listeners.add(listener);

            // 立即同步一次服务
            syncServiceFromConsul(serviceName);
            log.info("已订阅服务 {} 的地址变更", serviceName);
        }
    }

    @Override
    public void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName != null && listener != null) {
            Set<Consumer<List<String>>> listeners = addressChangeListeners.get(serviceName);
            if (listeners != null) {
                listeners.remove(listener);
                if (listeners.isEmpty()) {
                    addressChangeListeners.remove(serviceName);
                }
                log.info("已取消订阅服务 {} 的地址变更", serviceName);
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        // 关闭调度器
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        // 清除缓存和监听器
        serviceCache.clear();
        addressChangeListeners.clear();
        retryableMethodCache.clear();

        log.info("ConsulServiceCenter已关闭");
    }
}
