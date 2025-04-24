package com.weihua.rpc.core.client.invoker;

import com.weihua.rpc.common.util.ExponentialBackoff;
import com.weihua.rpc.core.client.config.ClientConfig;
import lombok.Getter;

/**
 * Invoker管理器配置类
 */
@Getter
public class InvokerManagerConfig {

    // 连接相关配置
    private final InvokerManager.ConnectionMode connectionMode;
    private final int maxRetryAttempts;
    private final ExponentialBackoff backoffStrategy;

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
}