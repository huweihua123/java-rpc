package com.weihua.rpc.core.client.registry.impl;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;
import com.weihua.rpc.core.client.registry.AbstractServiceDiscovery;
import com.weihua.rpc.core.server.annotation.MethodSignature;

import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Consul服务发现中心实现
 */
@Slf4j
public class ConsulServiceDiscovery extends AbstractServiceDiscovery {

    // 服务跟踪最长时间 (24小时)，可配置
    private static final long TRACKING_TIMEOUT_MS = 24 * 60 * 60 * 1000;
    // 失败计数阈值，超过将减少同步频率
    private static final int FAILURE_THRESHOLD = 5;
    // 最大退避时间(秒)
    private static final int MAX_BACKOFF_SECONDS = 300; // 5分钟
    // 可重试方法缓存
    private final Map<String, Boolean> retryableMethodCache = new ConcurrentHashMap<>();
    // 服务元数据缓存
    private final Map<String, Map<String, String>> serviceMetadataCache = new ConcurrentHashMap<>();
    // 同步锁，避免并发同步
    private final Map<String, Object> syncLocks = new ConcurrentHashMap<>();
    // 服务同步状态记录
    private final Map<String, Long> lastSyncTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> syncSuccessMap = new ConcurrentHashMap<>();
    // 跟踪服务和最后访问时间
    private final Map<String, Long> trackedServicesWithTimestamp = new ConcurrentHashMap<>();
    // 跟踪服务失败计数
    private final Map<String, Integer> serviceFailureCount = new ConcurrentHashMap<>();
    // 已订阅服务跟踪集合
    private final Set<String> subscribedServices = ConcurrentHashMap.newKeySet();

    private Consul consulClient;
    // 定时同步任务
    private ScheduledExecutorService scheduler;
    // 同步任务执行器
    private ExecutorService syncExecutor;

    @Override
    public void init() {
        // 初始化Consul客户端
        try {
            String address = discoveryConfig.getAddress();
            String host = address.split(":")[0];
            int port = Integer.parseInt(address.split(":")[1]);
            this.consulClient = Consul.builder().withUrl(String.format("http://%s:%d", host, port))
                    .withConnectTimeoutMillis(discoveryConfig.getConnectTimeout())
                    .withReadTimeoutMillis(discoveryConfig.getTimeout())
                    .build();

            log.info("Consul客户端初始化成功: {}:{}", host, port);
        } catch (Exception e) {
            log.error("初始化Consul客户端失败: {}", e.getMessage(), e);
            throw new RuntimeException("初始化Consul客户端失败", e);
        }

        // 初始化同步任务执行器
        this.syncExecutor = new ThreadPoolExecutor(
                1, // 核心线程数
                4, // 最大线程数
                60, TimeUnit.SECONDS, // 线程存活时间
                new LinkedBlockingQueue<>(1000), // 工作队列
                r -> {
                    Thread t = new Thread(r, "consul-sync-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );

        // 初始化定时同步调度器
        initScheduler();
    }

    /**
     * 初始化调度器
     */
    private void initScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "consul-sync-scheduler");
            t.setDaemon(true);
            log.info("Consul服务同步调度器已启动");
            return t;
        });

        // 定期同步已订阅的服务
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 同步所有跟踪的服务
                long now = System.currentTimeMillis();
                Set<String> services = new HashSet<>();

                // 添加缓存中的服务
                services.addAll(addressCache.getAllServiceNames());

                // 添加未过期的跟踪服务
                trackedServicesWithTimestamp.forEach((service, timestamp) -> {
                    if (now - timestamp < TRACKING_TIMEOUT_MS) {
                        services.add(service);
                    } else {
                        // 清理过期服务
                        log.info("清理过期服务跟踪: {}, 已跟踪{}小时无调用",
                                service, (now - timestamp) / 3600000);
                        trackedServicesWithTimestamp.remove(service);
                        serviceFailureCount.remove(service);
                    }
                });

                log.info("同步服务 {} 到Consul", services.size());

                for (String service : services) {
                    // 获取失败计数
                    int failCount = serviceFailureCount.getOrDefault(service, 0);

                    // 根据失败次数调整同步频率
                    if (failCount > FAILURE_THRESHOLD) {
                        // 计算跳过概率 (失败次数越多，越可能跳过)
                        int skipFactor = Math.min(failCount / FAILURE_THRESHOLD, 10);
                        if (skipFactor > 1 && ThreadLocalRandom.current().nextInt(skipFactor) != 0) {
                            // 大多数时间跳过同步，但仍然有1/skipFactor概率执行
                            continue;
                        }
                    }

                    // 使用执行器异步执行同步任务
                    syncExecutor.execute(() -> {
                        try {
                            log.info("同步服务 {} 到Consul", service);
                            boolean success = syncServiceFromConsul(service);

                            // 更新失败计数
                            if (success) {
                                // 同步成功，重置计数
                                serviceFailureCount.put(service, 0);
                            } else {
                                // 同步失败，增加计数
                                serviceFailureCount.compute(service, (k, v) -> v == null ? 1 : v + 1);
                                int newCount = serviceFailureCount.get(service);

                                if (newCount > FAILURE_THRESHOLD) {
                                    log.warn("服务 {} 同步持续失败 {} 次，将减少同步频率", service, newCount);
                                }
                            }
                        } catch (Exception e) {
                            log.error("同步服务 {} 失败: {}", service, e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                log.error("定时同步服务调度失败: {}", e.getMessage(), e);
            }
        }, 0, discoveryConfig.getSyncPeriod(), TimeUnit.SECONDS);
    }

    /**
     * 同步指定服务
     *
     * @param serviceName 服务名称
     * @return 同步是否成功
     */
    private boolean syncServiceFromConsul(String serviceName) {
        // 获取同步锁，避免并发同步同一服务
        Object syncLock = syncLocks.computeIfAbsent(serviceName, k -> new Object());

        synchronized (syncLock) {
            try {
                long startTime = System.currentTimeMillis();

                // 查询健康的服务实例
                HealthClient healthClient = consulClient.healthClient();
                List<ServiceHealth> services = healthClient.getHealthyServiceInstances(serviceName).getResponse();

                List<String> addresses = new ArrayList<>();
                Map<String, String> metadata = new HashMap<>();

                for (ServiceHealth service : services) {
                    String address = service.getService().getAddress();
                    int port = service.getService().getPort();

                    // 如果地址为空，使用节点地址
                    if (address == null || address.isEmpty()) {
                        address = service.getNode().getAddress();
                    }

                    addresses.add(address + ":" + port);

                    // 收集第一个实例的元数据
                    if (metadata.isEmpty()) {
                        metadata.putAll(service.getService().getMeta());
                    }
                }

                // 更新到地址缓存
                addressCache.updateAddresses(serviceName, addresses);

                // 更新元数据缓存
                if (!metadata.isEmpty()) {
                    serviceMetadataCache.put(serviceName, metadata);
                }

                // 记录同步状态
                lastSyncTimeMap.put(serviceName, System.currentTimeMillis());
                syncSuccessMap.put(serviceName, true);

                long syncTime = System.currentTimeMillis() - startTime;
                log.debug("已同步服务 {} 实例列表，共 {} 个实例，耗时 {}ms",
                        serviceName, addresses.size(), syncTime);

                return true;
            } catch (Exception e) {
                // 记录失败状态
                lastSyncTimeMap.put(serviceName, System.currentTimeMillis());
                syncSuccessMap.put(serviceName, false);

                log.error("同步服务 {} 失败: {}", serviceName, e.getMessage(), e);
                return false;
            }
        }
    }

    @Override
    public List<Invoker> discoverInvokers(RpcRequest request) {
        String serviceName = request.getInterfaceName();

        // 无论服务是否存在，都记录这次尝试并更新时间戳
        trackedServicesWithTimestamp.put(serviceName, System.currentTimeMillis());

        // 确保服务被监听，即使当前没有地址
        ensureServiceWatched(serviceName);

        // 获取服务地址列表
        List<String> addresses = addressCache.getAddresses(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            // 如果缓存中没有，尝试同步
            if (syncServiceFromConsul(serviceName)) {
                log.info("服务 {} 地址缓存为空，尝试从Consul同步", serviceName);
                addresses = addressCache.getAddresses(serviceName);
            }

            if (addresses == null || addresses.isEmpty()) {
                log.warn("未发现服务: {}", serviceName);
                return Collections.emptyList();
            }
        }

        // 直接使用InvokerManager获取Invoker
        return invokerManager.getInvokers(serviceName);
    }

    /**
     * 确保服务被监听，即使当前没有可用地址
     * 这是为了服务后续上线时能够自动发现
     */
    private void ensureServiceWatched(String serviceName) {
        // 这个服务是否已经被订阅过了
        if (!subscribedServices.contains(serviceName)) {
            // 将服务添加到已订阅集合
            subscribedServices.add(serviceName);

            // 为服务注册地址变更监听器
            addressCache.subscribeAddressChange(serviceName, addresses -> {
                try {
                    if (addresses != null && !addresses.isEmpty()) {
                        log.info("服务 {} 地址变更通知: {} 个实例", serviceName, addresses.size());
                        // 更新InvokerManager中的地址
                        invokerManager.updateServiceAddresses(serviceName, addresses);
                    }
                } catch (Exception e) {
                    log.error("处理服务 {} 地址变更时出错: {}", serviceName, e.getMessage(), e);
                }
            });

            log.info("已为服务 {} 设置地址变更监听", serviceName);
        }
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
    public boolean isMethodRetryable(String methodSignature) {
        // 从缓存查询是否可重试
        boolean result = retryableMethodCache.computeIfAbsent(methodSignature,
                k -> checkMethodRetryable(methodSignature));
        if (result) {
            log.debug("方法 {} 被标记为可重试", methodSignature);
        }
        return result;
    }

    /**
     * 检查方法是否可重试
     */
    private boolean checkMethodRetryable(String methodSignature) {
        try {
            // 从方法签名提取服务名称
            String serviceName = extractServiceName(methodSignature);

            // 优先从元数据缓存获取
            Map<String, String> metadata = getServiceMetadata(serviceName);
            if (metadata != null && !metadata.isEmpty()) {
                // 使用统一的方法签名处理工具类
                String normalizedMethod = MethodSignature.normalizeMethodSignature(methodSignature);
                log.debug("检查方法是否可重试: 原始签名={}, 规范化键={}", methodSignature, normalizedMethod);

                // 1. 检查精确方法的可重试标记
                String retryableKey = "retryable-" + normalizedMethod;
                if (metadata.containsKey(retryableKey)) {
                    log.debug("找到方法{}的重试配置: {}", methodSignature, metadata.get(retryableKey));
                    return "true".equalsIgnoreCase(metadata.get(retryableKey));
                }

                // 2. 检查是否有方法重试配置
                String retryConfigKey = "retryconfig-" + normalizedMethod;
                if (metadata.containsKey(retryConfigKey)) {
                    // 只要有配置就认为是可重试的
                    log.debug("找到方法{}的重试配置信息", methodSignature);
                    return true;
                }

                log.debug("未找到方法{}的重试配置", methodSignature);
            } else {
                log.debug("服务{}没有元数据或元数据为空", serviceName);
            }

            return false;
        } catch (Exception e) {
            log.error("检查方法是否可重试失败: {}", methodSignature, e);
            return false;
        }
    }

    /**
     * 获取方法重试配置
     *
     * @param methodSignature 方法签名
     * @return 重试配置，如果没有则返回null
     */
    public Map<String, String> getMethodRetryConfig(String methodSignature) {
        try {
            // 从方法签名提取服务名称
            String serviceName = extractServiceName(methodSignature);

            // 获取服务元数据
            Map<String, String> metadata = getServiceMetadata(serviceName);
            if (metadata == null || metadata.isEmpty()) {
                return null;
            }

            Map<String, String> retryConfig = new HashMap<>();
            String normalizedMethod = MethodSignature.normalizeMethodSignature(methodSignature);

            // 提取重试相关的元数据
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                String key = entry.getKey();

                // 匹配特定方法的重试配置
                if (key.startsWith("retry-" + normalizedMethod + "-")) {
                    String configName = key.substring(("retry-" + normalizedMethod + "-").length());
                    retryConfig.put(configName, entry.getValue());
                    log.debug("匹配到方法{}的重试配置: {}={}", methodSignature, configName, entry.getValue());
                }
            }

            // 如果找到了配置，则添加基础标志
            if (!retryConfig.isEmpty()) {
                retryConfig.put("retryable", "true");
                log.debug("方法{}有{}条重试配置", methodSignature, retryConfig.size());
            }

            return retryConfig;
        } catch (Exception e) {
            log.error("获取方法重试配置失败: {}", methodSignature, e);
            return null;
        }
    }

    @Override
    public Map<String, String> getServiceMetadata(String serviceName) {
        // 从缓存获取
        Map<String, String> metadata = serviceMetadataCache.get(serviceName);
        if (metadata != null) {
            return new HashMap<>(metadata);
        }

        // 缓存中没有，尝试从Consul获取
        try {
            HealthClient healthClient = consulClient.healthClient();
            List<ServiceHealth> services = healthClient.getHealthyServiceInstances(serviceName).getResponse();

            if (!services.isEmpty()) {
                ServiceHealth service = services.get(0);
                Map<String, String> newMetadata = service.getService().getMeta();

                // 缓存元数据
                if (!newMetadata.isEmpty()) {
                    serviceMetadataCache.put(serviceName, newMetadata);
                    return new HashMap<>(newMetadata);
                }
            }
        } catch (Exception e) {
            log.error("获取服务元数据失败: {}", serviceName, e.getMessage());
        }

        return Collections.emptyMap();
    }

    /**
     * 从方法签名提取服务名称
     */
    private String extractServiceName(String methodSignature) {
        int hashIndex = methodSignature.indexOf('#');
        return hashIndex > 0 ? methodSignature.substring(0, hashIndex) : methodSignature;
    }

    @Override
    public void subscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName != null && listener != null) {
            // 无论服务是否存在，都记录这次尝试并更新时间戳
            trackedServicesWithTimestamp.put(serviceName, System.currentTimeMillis());

            // 委托给地址缓存
            addressCache.subscribeAddressChange(serviceName, listener);

            // 立即同步一次服务
            syncServiceFromConsul(serviceName);
            log.info("已订阅服务 {} 的地址变更", serviceName);
        }
    }

    @Override
    public void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName != null && listener != null) {
            // 委托给地址缓存
            addressCache.unsubscribeAddressChange(serviceName, listener);
            log.info("已取消订阅服务 {} 的地址变更", serviceName);
        }
    }

    @Override
    public boolean forceSync(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            return false;
        }

        try {
            // 无论服务是否存在，都记录这次尝试并更新时间戳
            trackedServicesWithTimestamp.put(serviceName, System.currentTimeMillis());
            return syncServiceFromConsul(serviceName);
        } catch (Exception e) {
            log.error("强制同步服务 {} 失败: {}", serviceName, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean isServiceHealthy(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            return false;
        }

        try {
            HealthClient healthClient = consulClient.healthClient();
            List<ServiceHealth> services = healthClient.getHealthyServiceInstances(serviceName).getResponse();
            return !services.isEmpty();
        } catch (Exception e) {
            log.error("检查服务 {} 健康状态失败: {}", serviceName, e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        // 关闭执行器
        if (syncExecutor != null) {
            syncExecutor.shutdownNow();
        }

        // 关闭调度器
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        // 清除缓存
        retryableMethodCache.clear();
        serviceMetadataCache.clear();
        syncLocks.clear();
        lastSyncTimeMap.clear();
        syncSuccessMap.clear();
        trackedServicesWithTimestamp.clear();
        serviceFailureCount.clear();

        log.info("ConsulServiceCenter已关闭");
    }
}