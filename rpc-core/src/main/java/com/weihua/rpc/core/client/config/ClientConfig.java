/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:51:39
 * @LastEditTime: 2025-04-23 15:38:57
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.core.client.config;

import com.weihua.rpc.core.client.invoker.InvokerManager.ConnectionMode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * 客户端配置
 */
@Getter
@Setter
@Slf4j
public class ClientConfig {

    // 通用超时配置
    private Duration timeout = Duration.ofSeconds(3);

    // 网络连接配置
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);

    // 重试配置
    private boolean retryEnable = true;
    private int maxRetryAttempts = 3;
    private Duration retryInterval = Duration.ofSeconds(1);
    private boolean retryOnlyIdempotent = true;

    // 指数退避策略配置
    private double backoffMultiplier = 2.0;
    private Duration maxBackoffTime = Duration.ofSeconds(30);
    private boolean addJitter = true;
    private Duration minRetryInterval = Duration.ofMillis(500);

    // 连接模式配置
    private ConnectionMode connectionMode = ConnectionMode.LAZY;

    // 心跳配置
    private Duration heartbeatInterval = Duration.ofSeconds(30);
    private Duration heartbeatTimeout = Duration.ofSeconds(5);

    // 负载均衡配置
    private String loadBalanceStrategy = "random";

    // 服务版本与分组
    private String serviceVersion = "1.0.0";
    private String serviceGroup = "default";

    // 熔断器配置
    private boolean circuitBreakerEnable = true;

    @PostConstruct
    public void init() {
        // 更新日志输出，包含配置信息
        log.info("已加载客户端配置: 超时={}ms, 连接超时={}ms, 请求超时={}ms, " +
                "重试={}(退避策略: 乘数={}, 最大时间={}ms, 随机抖动={}), 连接模式={}, 熔断器={}",
                timeout.toMillis(), connectTimeout.toMillis(), requestTimeout.toMillis(),
                retryEnable ? "启用(最大" + maxRetryAttempts + "次)" : "禁用",
                backoffMultiplier, maxBackoffTime.toMillis(), addJitter ? "启用" : "禁用",
                connectionMode,
                circuitBreakerEnable ? "启用" : "禁用");
    }
}