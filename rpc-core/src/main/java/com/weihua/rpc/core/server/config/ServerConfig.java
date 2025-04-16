/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:22:26
 * @LastEditTime: 2025-04-14 16:28:21
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;

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
     * 读空闲超时时间（秒）
     */
    private int readerIdleTime = 180;

    /**
     * 写空闲超时时间（秒）
     */
    private int writerIdleTime = 60;

    /**
     * 所有类型空闲超时时间（秒）
     */
    private int allIdleTime = 0;

    /**
     * 请求处理超时时间（毫秒）
     */
    private int requestTimeout = 5000;

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