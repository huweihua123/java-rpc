/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:36:03
 * @LastEditTime: 2025-04-10 02:36:05
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RPC服务端配置属性
 */
@Data
@ConfigurationProperties(prefix = "rpc.server")
public class RpcServerProperties {

    /**
     * 服务主机地址，默认为0.0.0.0表示绑定所有接口
     */
    private String host = "0.0.0.0";

    /**
     * 服务端口
     */
    private int port = 9000;

    /**
     * IO线程数量，默认为0表示使用处理器数量 * 2
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
    private int readerIdleTime = 60;

    /**
     * 写空闲超时时间（秒）
     */
    private int writerIdleTime = 0;

    /**
     * 所有类型空闲超时时间（秒）
     */
    private int allIdleTime = 0;

    /**
     * 请求处理超时时间（毫秒）
     */
    private int requestTimeout = 5000;

    /**
     * 是否自动启动服务
     */
    private boolean autoStart = true;

    /**
     * 限流相关配置
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * 限流配置
     */
    @Data
    public static class RateLimit {
        /**
         * 是否启用限流
         */
        private boolean enabled = true;

        /**
         * 默认QPS限制
         */
        private int defaultQps = 100;
    }
}
