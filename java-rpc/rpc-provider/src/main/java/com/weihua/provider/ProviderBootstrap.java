package com.weihua.provider;

import com.weihua.server.provider.ServiceProvider;
import com.weihua.server.server.RpcServer;
import com.weihua.server.server.impl.NettyRpcServer;
import common.bootstrap.RpcBootstrap;
import common.config.ConfigurationManager;
import com.weihua.server.config.ProviderConfigManager;
import lombok.extern.log4j.Log4j2;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * RPC服务提供者启动类
 * 负责初始化和启动RPC服务器，注册服务
 */
@Log4j2
public class ProviderBootstrap {

    // 用于保持服务运行的锁
    private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

    // 服务器实例
    private static RpcServer rpcServer;
    private static ServiceProvider serviceProvider;

    public static void main(String[] args) {
        try {
            // 启动服务器
            start();

            // 等待关闭信号
            log.info("服务提供者已启动，按Ctrl+C关闭");
            SHUTDOWN_LATCH.await();
        } catch (Exception e) {
            log.error("服务启动异常", e);
            System.exit(1);
        }
    }

    /**
     * 启动RPC服务器
     */
    public static void start() {
        // 初始化RPC提供者
        RpcBootstrap.initializeProvider();

        // 获取配置管理器
        ProviderConfigManager configManager = ProviderConfigManager.getInstance();

        // 打印关键配置参数
        logProviderConfig();

        // 获取服务主机和端口
        String host = configManager.getHost();
        if ("auto".equals(host)) {
            host = getLocalIp();
        }
        int port = configManager.getPort();

        log.info("RPC服务器将在 {}:{} 启动", host, port);

        // 创建服务提供者
        serviceProvider = new ServiceProvider(host, port);

        // 注册服务接口 - 从配置文件加载
        registerServicesFromConfig(serviceProvider);

        // 创建并启动RPC服务器
        rpcServer = new NettyRpcServer(serviceProvider);
        log.info("RPC服务器启动中...");

        // 在新线程中启动服务器，避免阻塞主线程
        Thread serverThread = new Thread(() -> {
            rpcServer.start(port);
        });
        serverThread.setDaemon(false);
        serverThread.setName("rpc-server-thread");
        serverThread.start();

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("接收到关闭信号，正在关闭RPC服务器...");
            shutdown();
        }));
    }

    /**
     * 记录关键的提供者配置信息
     */
    private static void logProviderConfig() {
        ProviderConfigManager config = ProviderConfigManager.getInstance();
        ConfigurationManager baseConfig = ConfigurationManager.getInstance();

        log.info("===== RPC Provider Configuration =====");
        log.info("应用名称: {}", baseConfig.getString("rpc.application.name", "java-rpc-provider"));
        log.info("注册中心类型: {}", baseConfig.getString("rpc.registry", "consul"));
        log.info("主机: {}", config.getHost());
        log.info("端口: {}", config.getPort());
        log.info("工作线程数: {}", config.getWorkerThreads());
        log.info("IO线程数: {}", config.getIoThreads());
        log.info("最大连接数: {}", config.getMaxConnections());
        log.info("序列化方式: {}", baseConfig.getString("rpc.serializer", "json"));
        log.info("动态注册: {}", config.isDynamicRegister());
        log.info("===================================");
    }

    /**
     * 从配置文件注册服务
     */
    @SuppressWarnings("unchecked")
    private static void registerServicesFromConfig(ServiceProvider serviceProvider) {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = ProviderBootstrap.class.getClassLoader().getResourceAsStream("services.yaml");

            if (inputStream != null) {
                // 解析YAML文件
                Map<String, Object> config = yaml.load(inputStream);
                List<Map<String, Object>> services = (List<Map<String, Object>>) config.get("services");

                if (services != null && !services.isEmpty()) {
                    for (Map<String, Object> serviceConfig : services) {
                        String interfaceName = (String) serviceConfig.get("interface");
                        String implementationName = (String) serviceConfig.get("implementation");

                        // 动态加载服务实现类
                        try {
                            Class<?> interfaceClass = Class.forName(interfaceName);
                            Class<?> implementationClass = Class.forName(implementationName);
                            Object serviceImpl = implementationClass.getDeclaredConstructor().newInstance();

                            // 注册服务
                            serviceProvider.provideServiceInterface(serviceImpl);
                            log.info("注册服务: {}, 实现类: {}", interfaceName, implementationName);
                        } catch (Exception e) {
                            log.error("注册服务失败: {}", interfaceName, e);
                        }
                    }
                    log.info("从配置文件注册了 {} 个服务", services.size());
                    return;
                }
            }

            // 如果配置文件不存在或格式不正确，使用硬编码方式注册默认服务
            log.warn("未找到有效的服务配置文件或服务列表为空，使用默认服务配置");
            registerDefaultServices(serviceProvider);

        } catch (Exception e) {
            log.error("从配置文件加载服务失败", e);
            log.info("使用默认服务配置");
            registerDefaultServices(serviceProvider);
        }
    }

    /**
     * 注册默认服务（兼容旧代码）
     */
    private static void registerDefaultServices(ServiceProvider serviceProvider) {
        try {
            // 创建用户服务实例
            Class<?> userServiceClass = Class.forName("com.weihua.service.impl.UserServiceImpl");
            Object userService = userServiceClass.getDeclaredConstructor().newInstance();

            // 注册服务
            serviceProvider.provideServiceInterface(userService);
            log.info("注册默认服务: com.weihua.service.UserService");
        } catch (Exception e) {
            log.error("注册默认服务失败", e);
        }
    }

    /**
     * 关闭RPC服务器
     */
    public static void shutdown() {
        if (rpcServer != null) {
            rpcServer.stop();
            log.info("RPC服务器已停止");
        }

        if (serviceProvider != null) {
            serviceProvider.shutdown();
            log.info("服务提供者已关闭");
        }

        SHUTDOWN_LATCH.countDown();
    }

    /**
     * 获取本机IP地址
     */
    private static String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("无法获取本机IP地址，使用127.0.0.1", e);
            return "127.0.0.1";
        }
    }
}