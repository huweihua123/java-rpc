/*
 * @Author: weihua hu
 * @Date: 2025-04-04 22:50:44
 * @LastEditTime: 2025-04-05 02:17:02
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.consumer;

import com.weihua.client.config.RegistryConfigManager;
import common.bootstrap.RpcBootstrap;
import common.config.ConfigurationManager;
import com.weihua.client.proxy.ClientProxy;
import lombok.extern.log4j.Log4j2;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * RPC消费者启动类
 */
@Log4j2
public class ConsumerBootstrap {

    private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);
    private static final Map<String, Object> PROXY_CACHE = new HashMap<>();
    private static ClientProxy clientProxy;

    public static void main(String[] args) {
        try {
            // 初始化客户端并创建代理
            initialize();

            // 从配置文件加载要使用的服务
            loadServicesFromConfig();

            // 阻塞主线程，等待关闭信号
            log.info("消费者已启动，按Ctrl+C关闭");
            SHUTDOWN_LATCH.await();
        } catch (Exception e) {
            log.error("消费者启动异常", e);
            System.exit(1);
        }
    }

    /**
     * 初始化RPC消费者
     */
    public static void initialize() {
        // 初始化RPC消费者环境
        RpcBootstrap.initializeConsumer();

        // 打印关键配置
        logConsumerConfig();

        // 获取配置的服务中心类型
        RegistryConfigManager regConfig = RegistryConfigManager.getInstance();
        String registryType = regConfig.getRegistryType();

        // 创建客户端代理
        clientProxy = new ClientProxy(registryType);
        log.info("RPC客户端代理已初始化，使用服务中心类型: {}", registryType);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("接收到关闭信号，正在关闭RPC消费者...");
            shutdown();
        }));
    }

    /**
     * 获取服务代理
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> serviceClass) {
        String serviceName = serviceClass.getName();

        // 检查缓存
        if (PROXY_CACHE.containsKey(serviceName)) {
            return (T) PROXY_CACHE.get(serviceName);
        }

        // 创建代理
        T serviceProxy = (T) clientProxy.getProxy(serviceClass);
        PROXY_CACHE.put(serviceName, serviceProxy);
        log.info("创建服务代理: {}", serviceName);

        return serviceProxy;
    }

    /**
     * 从配置文件加载服务
     */
    @SuppressWarnings("unchecked")
    private static void loadServicesFromConfig() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = ConsumerBootstrap.class.getClassLoader().getResourceAsStream("references.yaml");

            if (inputStream != null) {
                Map<String, Object> config = yaml.load(inputStream);
                List<String> references = (List<String>) config.get("references");

                if (references != null && !references.isEmpty()) {
                    for (String interfaceName : references) {
                        try {
                            Class<?> interfaceClass = Class.forName(interfaceName);
                            // 提前创建代理并缓存
                            Object proxy = getService(interfaceClass);
                            log.info("加载服务引用: {}", interfaceName);
                        } catch (ClassNotFoundException e) {
                            log.error("找不到服务接口: {}", interfaceName, e);
                        }
                    }
                    log.info("从配置文件加载了 {} 个服务引用", references.size());
                }
            }
        } catch (Exception e) {
            log.error("从配置文件加载服务引用失败", e);
        }
    }

    /**
     * 记录关键配置信息
     */
    private static void logConsumerConfig() {
        ConfigurationManager config = ConfigurationManager.getInstance();

        log.info("===== RPC Consumer Configuration =====");
        log.info("连接超时: {}ms", config.getInt("rpc.consumer.timeout", 3000));
        log.info("负载均衡策略: {}", config.getString("rpc.consumer.loadbalance", "random"));
        log.info("失败策略: {}", config.getString("rpc.consumer.failover", "failfast"));
        log.info("连接池启用: {}", config.getBoolean("rpc.consumer.connection.pool.enable", true));
        log.info("最大空闲连接: {}", config.getInt("rpc.consumer.connection.pool.maxIdle", 16));
        log.info("最小空闲连接: {}", config.getInt("rpc.consumer.connection.pool.minIdle", 4));
        log.info("连接空闲超时: {}ms", config.getLong("rpc.consumer.connection.idleTimeout", 30000));
        log.info("重试启用: {}", config.getBoolean("rpc.consumer.retry.enable", true));
        log.info("最大重试次数: {}", config.getInt("rpc.consumer.retry.maxTimes", 3));
        log.info("重试间隔: {}ms", config.getInt("rpc.consumer.retry.interval", 1000));
        log.info("熔断器失败阈值: {}", config.getInt("rpc.consumer.circuitbreaker.failures", 5));
        log.info("熔断器错误率: {}", config.getDouble("rpc.consumer.circuitbreaker.errorRate", 0.5));
        log.info("===================================");
    }

    /**
     * 关闭所有资源
     */
    public static void shutdown() {
        // 关闭可能需要释放的资源
        PROXY_CACHE.clear();
        SHUTDOWN_LATCH.countDown();
    }
}