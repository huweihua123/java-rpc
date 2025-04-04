package com.weihua.client.serverCenter.impl;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.weihua.client.cache.ServiceCache;
import com.weihua.client.config.RegistryConfigManager;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.balance.LoadBalance;
import common.message.RpcRequest;
import common.spi.ExtensionLoader;
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

    // 单例实例
    private static volatile ConsulServiceCenter instance;
    private Consul consulClient;
    private String consulHost;
    private int consulPort;
    private LoadBalance loadBalance;
    // 添加本地缓存引用
    private final ServiceCache serviceCache;
    // 定时同步任务
    private ScheduledExecutorService scheduledExecutor;
    // 存储可重试方法的缓存
    private final Map<String, List<String>> retryableMethodsCache = new ConcurrentHashMap<>();
    // 同步周期（秒）
    private int syncPeriodSeconds;
    // 配置管理器
    private final RegistryConfigManager configManager;

    /**
     * 提供给SPI机制使用的无参构造函数
     */
    public ConsulServiceCenter() {
        // 使用注册中心配置管理器
        this.configManager = RegistryConfigManager.getInstance();

        // 从配置管理器中获取配置
        this.consulHost = configManager.getConsulHost();
        this.consulPort = configManager.getConsulPort();
        int timeout = configManager.getConsulTimeout();
        this.syncPeriodSeconds = configManager.getConsulSyncPeriod();

        log.info("初始化Consul服务中心: {}:{}, 超时: {}ms, 同步周期: {}秒",
                consulHost, consulPort, timeout, syncPeriodSeconds);

        // 初始化Consul客户端
        this.consulClient = Consul.builder()
                .withUrl(String.format("http://%s:%d", consulHost, consulPort))
                .withReadTimeoutMillis(timeout)
                .withConnectTimeoutMillis(configManager.getConsulConnectTimeout())
                .withWriteTimeoutMillis(configManager.getConsulWriteTimeout())
                .build();

        // 使用SPI机制获取负载均衡实现
        String loadBalanceStrategy = configManager.getLoadBalanceStrategy();
        this.loadBalance = ExtensionLoader
                .getExtensionLoader(LoadBalance.class)
                .getExtension(loadBalanceStrategy);
        log.info("使用负载均衡策略: {}", loadBalanceStrategy);

        // 初始化本地缓存
        this.serviceCache = new ServiceCache();

        // 初始化定时任务线程池
        initScheduler();

        // 启动定时同步任务
        startSyncTask(this.syncPeriodSeconds);
    }

    /**
     * 初始化调度器
     */
    private void initScheduler() {
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "consul-sync-thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 获取单例实例
     */
    public static ConsulServiceCenter getInstance() {
        if (instance == null) {
            synchronized (ConsulServiceCenter.class) {
                if (instance == null) {
                    instance = new ConsulServiceCenter();
                }
            }
        }
        return instance;
    }

    /**
     * 刷新配置
     */
    public void refreshConfig() {
        // 从配置管理器获取最新配置
        String newHost = configManager.getConsulHost();
        int newPort = configManager.getConsulPort();
        int timeout = configManager.getConsulTimeout();

        // 如果地址或端口变更，需要重新创建客户端
        if (!newHost.equals(consulHost) || newPort != consulPort) {
            log.info("Consul地址变更: {}:{} -> {}:{}", consulHost, consulPort, newHost, newPort);
            this.consulHost = newHost;
            this.consulPort = newPort;

            // 重新创建客户端
            this.consulClient = Consul.builder()
                    .withUrl(String.format("http://%s:%d", consulHost, consulPort))
                    .withReadTimeoutMillis(timeout)
                    .withConnectTimeoutMillis(configManager.getConsulConnectTimeout())
                    .withWriteTimeoutMillis(configManager.getConsulWriteTimeout())
                    .build();
        }

        // 更新负载均衡策略
        String loadBalanceStrategy = configManager.getLoadBalanceStrategy();
        this.loadBalance = ExtensionLoader
                .getExtensionLoader(LoadBalance.class)
                .getExtension(loadBalanceStrategy);
        log.info("更新负载均衡策略: {}", loadBalanceStrategy);

        // 更新同步周期
        int newSyncPeriod = configManager.getConsulSyncPeriod();
        if (newSyncPeriod != this.syncPeriodSeconds) {
            log.info("更新同步周期: {} -> {}", this.syncPeriodSeconds, newSyncPeriod);
            this.syncPeriodSeconds = newSyncPeriod;

            // 重启定时任务
            if (scheduledExecutor != null) {
                scheduledExecutor.shutdown();
            }
            initScheduler();
            startSyncTask(this.syncPeriodSeconds);
        }

        log.info("Consul服务中心配置已刷新");
    }

    /**
     * 启动定时同步任务
     */
    private void startSyncTask(int periodSeconds) {
        scheduledExecutor.scheduleAtFixedRate(
                this::syncAllServices,
                0,
                periodSeconds,
                TimeUnit.SECONDS);
        log.info("已启动Consul服务同步任务，同步周期: {}秒", periodSeconds);
    }

    /**
     * 同步所有已知服务
     */
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

    /**
     * 同步指定服务的实例信息
     */
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

    /**
     * 字符串解析为地址
     */
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

    /**
     * 从方法签名中提取服务名称
     */
    private String extractServiceName(String methodSignature) {
        // 方法签名格式: com.example.Service#methodName(args)
        int hashIndex = methodSignature.indexOf('#');
        return hashIndex > 0 ? methodSignature.substring(0, hashIndex) : methodSignature;
    }

    /**
     * 检查方法是否被标记为可重试
     */
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

    /**
     * 从Consul获取服务的可重试方法列表
     */
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
            if (scheduledExecutor != null) {
                scheduledExecutor.shutdown();
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
                log.info("关闭Consul同步任务");
            }
        } catch (Exception e) {
            log.error("关闭Consul同步任务失败", e);
            if (scheduledExecutor != null) {
                scheduledExecutor.shutdownNow();
            }
        }

        // Consul客户端资源释放
        try {
            log.info("关闭Consul客户端连接");
        } catch (Exception e) {
            log.error("关闭Consul客户端连接失败", e);
        }
    }
}