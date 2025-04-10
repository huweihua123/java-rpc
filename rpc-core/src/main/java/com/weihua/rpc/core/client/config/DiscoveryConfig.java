/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:01:21
 * @LastEditTime: 2025-04-10 16:31:17
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 注册中心配置
 */
@Component
@Getter
@Setter
public class DiscoveryConfig {

    /**
     * 注册中心类型
     */
    @Value("${rpc.discovery:consul}")
    private String type;

    /**
     * 注册中心地址
     */
    @Value("${rpc.discovery.address:127.0.0.1:8500}")
    private String address;

    /**
     * 连接超时（毫秒）
     */
    @Value("${rpc.discovery.connect.timeout:5000}")
    private int connectTimeout;

    /**
     * 请求超时（毫秒）
     */
    @Value("${rpc.discovery.timeout:5000}")
    private int timeout;

    /**
     * 重试次数
     */
    @Value("${rpc.discovery.retry.times:3}")
    private int retryTimes;

    /**
     * 同步周期（秒）
     */
    @Value("${rpc.discovery.sync.period:30}")
    private int syncPeriod;

    @PostConstruct
    public void init() {
//        System.out.println("注册中心配置: 类型=" + type + ", 地址=" + address +
//                ", 连接超时=" + connectTimeout + "ms, 请求超时=" + timeout +
//                "ms, 重试次数=" + retryTimes + ", 同步周期=" + syncPeriod + "s");
    }
}
