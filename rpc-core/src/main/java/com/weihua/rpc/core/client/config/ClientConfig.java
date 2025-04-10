package com.weihua.rpc.core.client.config;

import com.weihua.rpc.common.config.Configuration;
import com.weihua.rpc.common.config.SpringConfigurationAdapter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 客户端配置
 * 使用Spring管理的配置类，从Spring环境中加载配置
 */
@Component
@Getter
@Setter
@Slf4j
public class ClientConfig {

    // 注入Spring环境配置
    @Autowired
    private Environment environment;

    // 通用超时配置 - 添加这个字段
    @Value("${rpc.client.timeout:3000}")
    private int timeout;

    // 网络连接配置
    @Value("${rpc.client.connect.timeout:3000}")
    private int connectTimeout;

    @Value("${rpc.client.request.timeout:5}")
    private int requestTimeout;

    @Value("${rpc.client.connections.max:4}")
    private int maxConnectionsPerAddress;

    @Value("${rpc.client.connections.init:1}")
    private int initConnectionsPerAddress;

    // 重试配置
    @Value("${rpc.client.retry.enable:true}")
    private boolean retryEnable;

    @Value("${rpc.client.retry.max:2}")
    private int maxRetries;

    @Value("${rpc.client.retry.interval:30000}")
    private int retryIntervalMillis;

    @Value("${rpc.client.retry.only.idempotent:true}")
    private boolean retryOnlyIdempotent;

    // 心跳配置
    @Value("${rpc.client.heartbeat.interval:30}")
    private int heartbeatInterval;

    @Value("${rpc.client.heartbeat.timeout:5}")
    private int heartbeatTimeout;

    // 负载均衡配置
    @Value("${rpc.loadBalance.type:random}")
    private String loadBalanceStrategy;

    // 服务版本与分组
    @Value("${rpc.service.version:1.0.0}")
    private String serviceVersion;

    @Value("${rpc.service.group:default}")
    private String serviceGroup;

    // 熔断器配置 - 添加这个字段
    @Value("${rpc.client.circuit-breaker.enable:true}")
    private boolean circuitBreakerEnable;

    /**
     * 配置适配器Bean，用于兼容原有代码
     */
    @Bean
    public Configuration rpcConfiguration() {
        return new SpringConfigurationAdapter(environment);
    }

    /**
     * 获取特定接口的请求超时时间(秒)
     */
    public int getInterfaceRequestTimeout(String interfaceName) {
        String key = "rpc.client.interfaces." + interfaceName + ".request.timeout";
        return environment.getProperty(key, Integer.class, requestTimeout);
    }

    /**
     * 获取特定接口的最大重试次数
     */
    public int getInterfaceMaxRetries(String interfaceName) {
        String key = "rpc.client.interfaces." + interfaceName + ".retry.max";
        return environment.getProperty(key, Integer.class, maxRetries);
    }

    @PostConstruct
    public void init() {
        // 更新日志输出，包含新增字段
        log.info("已加载客户端配置: 超时={}ms, 连接超时={}ms, 请求超时={}s, 最大连接数={}, 初始连接数={}, 重试={}, 熔断器={}",
                timeout, connectTimeout, requestTimeout,
                maxConnectionsPerAddress, initConnectionsPerAddress,
                retryEnable ? "启用(最大" + maxRetries + "次)" : "禁用",
                circuitBreakerEnable ? "启用" : "禁用");
    }
}