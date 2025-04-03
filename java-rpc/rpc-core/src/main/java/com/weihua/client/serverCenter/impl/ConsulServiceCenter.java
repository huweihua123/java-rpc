package com.weihua.client.serverCenter.impl;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.weihua.client.cache.ServiceCache;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.balance.LoadBalance;
import com.weihua.client.serverCenter.balance.impl.ConsistencyHashBalance;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ConsulServiceCenter implements ServiceCenter {

    // 同步周期（秒）
    private static final int SYNC_PERIOD_SECONDS = 30;
    // 单例实例
    private static volatile ConsulServiceCenter instance;
    private final Consul consulClient;
    private final String consulHost;
    private final int consulPort;
    private final LoadBalance loadBalance;
    // 添加本地缓存引用
    private final ServiceCache serviceCache;
    // 定时同步任务
    private final ScheduledExecutorService scheduledExecutor;
    // 存储可重试方法的缓存
    private final Map<String, List<String>> retryableMethodsCache = new ConcurrentHashMap<>();

    // 将构造函数改为私有
    private ConsulServiceCenter(String host, int port) {
        this.consulHost = host;
        this.consulPort = port;
        // 使用Orbitz的Consul客户端库
        this.consulClient = Consul.builder()
                .withUrl(String.format("http://%s:%d", host, port))
                .withReadTimeoutMillis(20000)
                .withConnectTimeoutMillis(10000)
                .withWriteTimeoutMillis(10000)
                .build();
        // 使用一致性哈希负载均衡
        this.loadBalance = new ConsistencyHashBalance();
        // 初始化本地缓存
        this.serviceCache = new ServiceCache();
        // 初始化定时任务线程池
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "consul-sync-thread");
            t.setDaemon(true);
            return t;
        });
        // 启动定时同步任务
        startSyncTask();
    }

    // 获取单例实例的方法
    public static ConsulServiceCenter getInstance(String host, int port) {
        if (instance == null) {
            synchronized (ConsulServiceCenter.class) {
                if (instance == null) {
                    instance = new ConsulServiceCenter(host, port);
                }
            }
        }
        return instance;
    }

    // 提供默认参数的获取实例方法
    public static ConsulServiceCenter getInstance() {
        return getInstance("localhost", 8500);
    }

    // 启动定时同步任务
    private void startSyncTask() {
        scheduledExecutor.scheduleAtFixedRate(
                this::syncAllServices,
                0,
                SYNC_PERIOD_SECONDS,
                TimeUnit.SECONDS);
        log.info("已启动Consul服务同步任务，同步周期: {}秒", SYNC_PERIOD_SECONDS);
    }

    // 同步所有已知服务
    private void syncAllServices() {
        try {
            // 获取Consul中所有服务名称
            Map<String, List<String>> catalogServices = consulClient.catalogClient().getServices().getResponse();
            for (String serviceName : catalogServices.keySet()) {
                syncServiceFromConsul(serviceName);
            }
            log.debug("已完成所有服务的同步");
        } catch (Exception e) {
            log.error("同步服务列表时发生错误", e);
        }
    }

    // 同步指定服务的实例信息
    public void syncServiceFromConsul(String serviceName) {
        try {
            // 查询健康的服务实例
            HealthClient healthClient = consulClient.healthClient();
            List<ServiceHealth> serviceHealthList = healthClient.getHealthyServiceInstances(serviceName).getResponse();

            List<String> currentAddresses = new ArrayList<>();
            for (ServiceHealth serviceHealth : serviceHealthList) {
                String address = serviceHealth.getService().getAddress();
                int port = serviceHealth.getService().getPort();

                if (address == null || address.isEmpty()) {
                    address = serviceHealth.getNode().getAddress();
                }

                String serviceAddress = address + ":" + port;
                currentAddresses.add(serviceAddress);
                serviceCache.addServcieToCache(serviceName, serviceAddress);
            }

            // 检查缓存中是否有需要移除的地址
            List<String> cachedAddresses = serviceCache.getServiceFromCache(serviceName);
            if (cachedAddresses != null) {
                for (String cachedAddress : new ArrayList<>(cachedAddresses)) {
                    if (!currentAddresses.contains(cachedAddress)) {
                        serviceCache.delete(serviceName, cachedAddress);
                        log.info("从缓存中移除不可用的服务实例: {} - {}", serviceName, cachedAddress);
                    }
                }
            }

            log.debug("已同步服务 {} 的实例信息，共 {} 个实例", serviceName, currentAddresses.size());
        } catch (Exception e) {
            log.error("同步服务 {} 信息时发生错误", serviceName, e);
        }
    }

    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest rpcRequest) {
        String serviceName = rpcRequest.getInterfaceName();

        // 先从本地缓存获取服务地址
        List<String> cachedAddresses = serviceCache.getServiceFromCache(serviceName);

        if (cachedAddresses != null && !cachedAddresses.isEmpty()) {
            // 使用负载均衡从缓存中选择地址
            String selectedAddress = loadBalance.balance(cachedAddresses);
            log.info("从缓存中发现服务: {} 地址: {}", serviceName, selectedAddress);
            return parseAddress(selectedAddress);
        }

        // 缓存未命中，从Consul获取并更新缓存
        try {
            // 同步服务信息到缓存
            syncServiceFromConsul(serviceName);

            // 再次从缓存获取
            cachedAddresses = serviceCache.getServiceFromCache(serviceName);

            if (cachedAddresses != null && !cachedAddresses.isEmpty()) {
                String selectedAddress = loadBalance.balance(cachedAddresses);
                log.info("从Consul同步后发现服务: {} 地址: {}", serviceName, selectedAddress);
                return parseAddress(selectedAddress);
            } else {
                log.warn("未找到服务: {}", serviceName);
                return null;
            }
        } catch (Exception e) {
            log.error("服务发现失败，服务名: {}", serviceName, e);
            return null;
        }
    }

    // 字符串解析为地址
    private InetSocketAddress parseAddress(String address) {
        String[] result = address.split(":");
        return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
    }

    @Override
    public boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature) {
        // 1. 解析方法签名，获取服务名称
        String serviceName = extractServiceName(methodSignature);

        // 2. 判断方法是否为可重试方法（幂等操作）
        if (!isMethodRetryable(serviceName, methodSignature)) {
            log.info("方法 {} 非幂等操作，不支持重试", methodSignature);
            return false;
        }

        log.info("方法 {} 为幂等操作，支持重试", methodSignature);
        return true;
    }

    // 从方法签名中提取服务名称
    private String extractServiceName(String methodSignature) {
        // 方法签名格式: com.example.Service#methodName(args)
        int hashIndex = methodSignature.indexOf('#');
        return hashIndex > 0 ? methodSignature.substring(0, hashIndex) : methodSignature;
    }

    // 检查方法是否被标记为可重试
    private boolean isMethodRetryable(String serviceName, String methodSignature) {
        // 1. 尝试从缓存获取可重试方法列表
        List<String> retryableMethods = retryableMethodsCache.get(serviceName);

        // 2. 缓存未命中，从Consul获取
        if (retryableMethods == null) {
            retryableMethods = fetchRetryableMethodsFromConsul(serviceName);
            if (retryableMethods != null && !retryableMethods.isEmpty()) {
                retryableMethodsCache.put(serviceName, retryableMethods);
            } else {
                log.debug("服务 {} 未找到可重试方法信息", serviceName);
                return false;
            }
        }

        // 3. 检查方法是否在可重试列表中
        return retryableMethods.contains(methodSignature);
    }

    // 从Consul获取服务的可重试方法列表
    private List<String> fetchRetryableMethodsFromConsul(String serviceName) {
        try {
            List<String> retryableMethods = new ArrayList<>();

            // 获取服务实例
            List<ServiceHealth> healthServices = consulClient.healthClient()
                    .getHealthyServiceInstances(serviceName).getResponse();

            if (healthServices != null && !healthServices.isEmpty()) {
                // 从第一个实例的元数据中提取可重试方法信息
                Map<String, String> meta = healthServices.get(0).getService().getMeta();

                // 遍历元数据，查找标记为可重试的方法
                if (meta != null) {
                    for (Map.Entry<String, String> entry : meta.entrySet()) {
                        if (entry.getKey().startsWith("retryable-")) {
                            retryableMethods.add(entry.getValue());
                        }
                    }
                }
            }

            log.debug("从Consul获取到服务 {} 的可重试方法: {}", serviceName, retryableMethods);
            return retryableMethods;
        } catch (Exception e) {
            log.error("获取服务 {} 可重试方法失败: {}", serviceName, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void close() {
        // 关闭定时任务线程池
        try {
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            log.info("关闭Consul同步任务");
        } catch (Exception e) {
            log.error("关闭Consul同步任务失败", e);
            scheduledExecutor.shutdownNow();
        }

        // Consul客户端资源释放
        try {
            log.info("关闭Consul客户端连接");
        } catch (Exception e) {
            log.error("关闭Consul客户端连接失败", e);
        }
    }
}
