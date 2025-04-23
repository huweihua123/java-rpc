/*
 * @Author: weihua hu
 * @Date: 2025-04-15 01:33:49
 * @LastEditTime: 2025-04-23 16:08:55
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.configurer;

import com.weihua.rpc.core.condition.ConditionalOnServerMode;
import com.weihua.rpc.core.server.config.ServerConfig;
import com.weihua.rpc.springboot.properties.RpcServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * 服务器配置绑定器
 * 负责将SpringBoot配置属性绑定到核心服务器配置对象
 */
@Configuration
@ConditionalOnServerMode
@EnableConfigurationProperties(RpcServerProperties.class)
public class ServerConfigurer {

    /**
     * 创建服务器配置对象并绑定属性
     * 
     * @param properties 服务器配置属性
     * @param env        环境变量，用于获取额外的配置参数
     * @return 服务器配置对象
     */
    @Bean
    public ServerConfig serverConfig(RpcServerProperties properties, Environment env) {
        ServerConfig config = new ServerConfig();

        // 基础属性映射
        config.setHost(properties.getHost());
        config.setPort(properties.getPort());

        // 从环境变量读取更多高级配置
        Integer ioThreads = env.getProperty("rpc.server.io-threads", Integer.class);
        if (ioThreads != null && ioThreads > 0) {
            config.setIoThreads(ioThreads);
        }

        Integer workerThreads = env.getProperty("rpc.server.worker-threads", Integer.class);
        if (workerThreads != null && workerThreads > 0) {
            config.setWorkerThreads(workerThreads);
        }

        Integer maxConnections = env.getProperty("rpc.server.max-connections", Integer.class);
        if (maxConnections != null && maxConnections > 0) {
            config.setMaxConnections(maxConnections);
        }

        // 空闲超时配置 - 直接使用Duration属性而非废弃方法
        Duration readerIdleTime = env.getProperty("rpc.server.reader-idle-time", Duration.class);
        if (readerIdleTime != null) {
            config.setReaderIdleTime(readerIdleTime);
        } else {
            // 直接访问RpcServerProperties中的Duration属性
            config.setReaderIdleTime(properties.getReaderIdleTime());
        }

        Duration writerIdleTime = env.getProperty("rpc.server.writer-idle-time", Duration.class);
        if (writerIdleTime != null) {
            config.setWriterIdleTime(writerIdleTime);
        } else {
            // 直接访问RpcServerProperties中的Duration属性
            config.setWriterIdleTime(properties.getWriterIdleTime());
        }

        Duration allIdleTime = env.getProperty("rpc.server.all-idle-time", Duration.class);
        if (allIdleTime != null) {
            config.setAllIdleTime(allIdleTime);
        } else {
            // 直接访问RpcServerProperties中的Duration属性
            config.setAllIdleTime(properties.getAllIdleTime());
        }

        // 请求处理超时
        Duration requestTimeout = env.getProperty("rpc.server.request-timeout", Duration.class);
        if (requestTimeout != null) {
            config.setRequestTimeout(requestTimeout);
        } else {
            // 直接访问RpcServerProperties中的Duration属性
            config.setRequestTimeout(properties.getRequestTimeout());
        }

        return config;
    }
}