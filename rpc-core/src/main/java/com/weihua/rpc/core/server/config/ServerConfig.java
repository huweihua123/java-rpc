/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:22:26
 * @LastEditTime: 2025-04-10 02:22:28
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 服务器配置
 */
@Component
@Getter
@Setter
public class ServerConfig {

    /**
     * 服务绑定主机名
     */
    @Value("${rpc.server.host:0.0.0.0}")
    private String host;

    /**
     * 服务端口
     */
    @Value("${rpc.server.port:9000}")
    private int port;

    /**
     * IO线程数量
     */
    @Value("${rpc.server.io.threads:0}")
    private int ioThreads;

    /**
     * 工作线程数量
     */
    @Value("${rpc.server.worker.threads:200}")
    private int workerThreads;

    /**
     * 最大连接数
     */
    @Value("${rpc.server.max.connections:10000}")
    private int maxConnections;

    /**
     * 读空闲超时时间（秒）
     */
    @Value("${rpc.server.idle.timeout.reader:60}")
    private int readerIdleTime;

    /**
     * 写空闲超时时间（秒）
     */
    @Value("${rpc.server.idle.timeout.writer:0}")
    private int writerIdleTime;

    /**
     * 所有类型空闲超时时间（秒）
     */
    @Value("${rpc.server.idle.timeout.all:0}")
    private int allIdleTime;

    /**
     * 请求处理超时时间（毫秒）
     */
    @Value("${rpc.server.request.timeout:5000}")
    private int requestTimeout;

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        // 如果IO线程数为0，则使用默认值（处理器数量 * 2）
        if (ioThreads <= 0) {
            ioThreads = Runtime.getRuntime().availableProcessors() * 2;
        }

        System.out.println("服务器配置: " +
                "地址=" + host + ":" + port + ", " +
                "IO线程=" + ioThreads + ", " +
                "工作线程=" + workerThreads + ", " +
                "最大连接数=" + maxConnections);
    }
}
