/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:33:10
 * @LastEditTime: 2025-04-10 01:33:12
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RPC配置属性
 */
@Data
@ConfigurationProperties(prefix = "rpc")
public class RpcProperties {

    /**
     * 服务提供者配置
     */
    private ProviderConfig provider = new ProviderConfig();

    /**
     * 服务消费者配置
     */
    private ConsumerConfig consumer = new ConsumerConfig();

    /**
     * 注册中心配置
     */
    private RegistryConfig registry = new RegistryConfig();

    @Data
    public static class ProviderConfig {
        /**
         * 服务主机地址，默认为本地
         */
        private String host = "127.0.0.1";

        /**
         * 服务端口，默认9000
         */
        private int port = 9000;
    }

    @Data
    public static class ConsumerConfig {
        /**
         * 默认超时时间，单位：毫秒
         */
        private int timeout = 3000;
    }

    @Data
    public static class RegistryConfig {
        /**
         * 注册中心类型：memory、zookeeper等
         */
        private String type = "memory";

        /**
         * 注册中心地址，如zookeeper的地址
         */
        private String address = "";
    }
}
