/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:22:26
 * @LastEditTime: 2025-04-24 19:16:28
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * 服务器配置
 */
@Getter
@Setter
@Slf4j
public class ServerConfig {

    /**
     * 服务绑定主机名
     */
    private String host = "0.0.0.0";

    /**
     * 服务端口
     */
    private int port = 9000;

    /**
     * IO线程数量
     */
    private int ioThreads = 0;

    /**
     * 工作线程数量
     */
    private int workerThreads = 200;

    /**
     * 最大连接数
     */
    private int maxConnections = 10000;

    /**
     * 读空闲超时时间
     */
    private Duration readerIdleTime = Duration.ofSeconds(15);

    /**
     * 写空闲超时时间
     */
    private Duration writerIdleTime = Duration.ofSeconds(10);

    /**
     * 所有类型空闲超时时间
     */
    private Duration allIdleTime = Duration.ofSeconds(0);

    /**
     * 请求处理超时时间
     */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        // 如果IO线程数为0，则使用默认值（处理器数量 * 2）
        if (ioThreads <= 0) {
            ioThreads = Runtime.getRuntime().availableProcessors() * 2;
        }

        log.info("服务器配置: 地址={}:{}, IO线程={}, 工作线程={}, 最大连接数={}",
                host, port, ioThreads, workerThreads, maxConnections);
    }
}