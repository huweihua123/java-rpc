/*
 * @Author: weihua hu
 * @Date: 2025-04-23 21:05:12
 * @LastEditTime: 2025-04-23 21:05:14
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.invoker;

import com.weihua.rpc.common.util.ExponentialBackoff;
import com.weihua.rpc.core.client.config.ClientConfig;
import lombok.Getter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Invoker管理器配置类
 * 负责封装和管理所有InvokerManager相关的配置参数
 */
@Getter
public class InvokerManagerConfig {

    // 连接相关配置
    private final InvokerManager.ConnectionMode connectionMode;
    private final int maxRetryAttempts;
    private final ExponentialBackoff backoffStrategy;

    // 监控相关配置
    private final int initialDelaySeconds = 5;
    private final int checkIntervalSeconds = 10;

    /**
     * 通过ClientConfig创建InvokerManagerConfig
     */
    public InvokerManagerConfig(ClientConfig clientConfig) {
        this.connectionMode = clientConfig.getConnectionMode();
        this.maxRetryAttempts = clientConfig.getMaxRetryAttempts();
        this.backoffStrategy = createBackoffStrategy(clientConfig);
    }

    /**
     * 创建退避策略
     */
    private ExponentialBackoff createBackoffStrategy(ClientConfig config) {
        return ExponentialBackoff.builder()
                .baseIntervalMs((int) config.getRetryInterval().toMillis())
                .multiplier(config.getBackoffMultiplier())
                .maxIntervalMs((int) config.getMaxBackoffTime().toMillis())
                .minIntervalMs((int) config.getMinRetryInterval().toMillisPart())
                .addJitter(config.isAddJitter())
                .build();
    }

    /**
     * 创建调度器
     */
    public ScheduledExecutorService createScheduler() {
        return Executors.newSingleThreadScheduledExecutor(createThreadFactory());
    }

    /**
     * 创建线程工厂
     */
    private ThreadFactory createThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "invoker-health-check");
            t.setDaemon(true);
            return t;
        };
    }
}