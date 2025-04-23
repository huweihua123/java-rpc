/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:36:03
 * @LastEditTime: 2025-04-23 16:16:06
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
     * 读空闲超时时间
     */
    private Duration readerIdleTime = Duration.ofSeconds(180);

    /**
     * 写空闲超时时间
     */
    private Duration writerIdleTime = Duration.ofSeconds(60);

    /**
     * 所有类型空闲超时时间
     */
    private Duration allIdleTime = Duration.ofSeconds(0);

    /**
     * 请求处理超时时间
     */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /**
     * 是否自动启动服务
     */
    private boolean autoStart = true;

    /**
     * 服务名称/标识符
     * 用于日志和监控
     */
    private String name = "rpc-server";

    /**
     * 优雅关闭超时时间
     * 关闭服务器时等待处理中请求的最长时间
     */
    private Duration shutdownTimeout = Duration.ofSeconds(15);

    /**
     * 最大消息长度（字节）
     * 防止异常大的请求导致内存溢出
     */
    private int maxMessageSize = 10 * 1024 * 1024; // 10MB

    /**
     * 是否启用TCP Keepalive
     */
    private boolean keepAlive = true;

    /**
     * TCP NoDelay选项
     */
    private boolean tcpNoDelay = true;

    /**
     * 接收缓冲区大小（字节）
     */
    private int receiveBufferSize = 65536;

    /**
     * 发送缓冲区大小（字节）
     */
    private int sendBufferSize = 65536;

    /**
     * 连接请求队列大小
     */
    private int backlog = 128;

    /**
     * IP白名单，允许访问的IP列表
     * 为空时表示允许所有IP
     */
    private List<String> allowedIps = new ArrayList<>();

    /**
     * SSL配置
     */
    private Ssl ssl = new Ssl();

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
        private Boolean enabled = false;

        /**
         * 默认QPS限制
         */
        private int defaultQps = 100;

        /**
         * 是否启用自适应限流
         */
        private boolean adaptiveQps = false;

        /**
         * 每个服务的最大QPS（服务级限流）
         */
        private int maxServiceQps = 5000;

        /**
         * 每个IP的最大QPS（IP级限流）
         */
        private int maxIpQps = 1000;

        /**
         * 令牌桶容量（突发流量处理能力）
         */
        private int burstCapacity = 50;
    }

    /**
     * SSL配置
     */
    @Data
    public static class Ssl {
        /**
         * 是否启用SSL
         */
        private boolean enabled = false;

        /**
         * 密钥库路径
         */
        private String keyStore = "";

        /**
         * 密钥库密码
         */
        private String keyStorePassword = "";

        /**
         * 密钥库类型，默认JKS
         */
        private String keyStoreType = "JKS";

        /**
         * 信任库路径
         */
        private String trustStore = "";

        /**
         * 信任库密码
         */
        private String trustStorePassword = "";

        /**
         * SSL协议
         */
        private String protocol = "TLS";

        /**
         * 是否需要客户端认证
         */
        private boolean clientAuth = false;
    }

}