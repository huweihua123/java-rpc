package com.weihua.server.provider;

import com.weihua.server.rateLimit.provider.RateLimitProvider;
import com.weihua.server.serviceCenter.ServiceRegister;
import common.config.ConfigurationManager;
import common.spi.ExtensionLoader;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class ServiceProvider {
    // 服务接口提供者映射
    private final Map<String, Object> interfaceProvider;
    // 限流提供者
    private final RateLimitProvider rateLimitProvider;
    // 服务端口
    private final int port;
    // 服务主机
    private final String host;
    // 服务注册中心
    private final ServiceRegister serviceRegister;

    /**
     * 构造函数
     * 
     * @param host 主机地址
     * @param port 端口号
     */
    public ServiceProvider(String host, int port) {
        this.host = host;
        this.port = port;
        this.interfaceProvider = new HashMap<>();

        // 使用单例模式获取RateLimitProvider
        this.rateLimitProvider = RateLimitProvider.getInstance();

        // 从配置中获取服务注册中心类型
        String registryType = ConfigurationManager.getInstance().getString("rpc.registry", "consul");
        log.info("使用服务注册中心类型: {}", registryType);

        // 使用SPI机制获取ServiceRegister实现
        this.serviceRegister = ExtensionLoader.getExtensionLoader(ServiceRegister.class)
                .getExtension(registryType);
        log.info("已初始化服务注册中心: {}", serviceRegister);
    }

    /**
     * 提供服务接口
     * 
     * @param service 服务实现实例
     */
    public void provideServiceInterface(Object service) {
        Class<?>[] interfaces = service.getClass().getInterfaces();
        for (Class<?> clazz : interfaces) {
            String interfaceName = clazz.getName();
            interfaceProvider.put(interfaceName, service);

            // 注册到服务中心
            serviceRegister.register(clazz, new InetSocketAddress(host, port));
            log.info("已注册服务: {}, 地址: {}:{}", interfaceName, host, port);
        }
    }

    /**
     * 获取限流提供者
     * 
     * @return 限流提供者
     */
    public RateLimitProvider getRateLimitProvider() {
        return rateLimitProvider;
    }

    /**
     * 获取服务实例
     * 
     * @param interfaceName 接口名称
     * @return 服务实例
     */
    public Object getService(String interfaceName) {
        return interfaceProvider.get(interfaceName);
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        if (serviceRegister != null) {
            try {
//                serviceRegister.shutdown();
                log.info("服务注册中心已关闭");
            } catch (Exception e) {
                log.error("关闭服务注册中心时发生错误", e);
            }
        }
    }
}