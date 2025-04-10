/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:43:22
 * @LastEditTime: 2025-04-10 01:43:31
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.constants;

/**
 * RPC 框架常量
 */
public class RpcConstants {

    /**
     * 默认版本号
     */
    public static final String DEFAULT_VERSION = "1.0.0";

    /**
     * 默认分组
     */
    public static final String DEFAULT_GROUP = "default";

    /**
     * 配置相关常量
     */
    public static class Config {
        /**
         * 配置前缀
         */
        public static final String PREFIX = "rpc.";

        /**
         * 注册中心相关配置前缀
         */
        public static final String REGISTRY_PREFIX = PREFIX + "registry.";

        /**
         * 服务提供者相关配置前缀
         */
        public static final String PROVIDER_PREFIX = PREFIX + "provider.";

        /**
         * 服务消费者相关配置前缀
         */
        public static final String CONSUMER_PREFIX = PREFIX + "consumer.";
    }

    /**
     * 序列化相关常量
     */
    public static class Serialization {
        /**
         * JSON 序列化类型
         */
        public static final String JSON = "json";

        /**
         * Hessian2 序列化类型
         */
        public static final String HESSIAN2 = "hessian2";

        /**
         * Protobuf 序列化类型
         */
        public static final String PROTOBUF = "protobuf";
    }

    /**
     * 协议相关常量
     */
    public static class Protocol {
        /**
         * 默认端口
         */
        public static final int DEFAULT_PORT = 9000;
    }
}
