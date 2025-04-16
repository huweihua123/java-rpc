/*
 * @Author: weihua hu
 * @Date: 2025-04-16 22:09:55
 * @LastEditTime: 2025-04-16 22:09:57
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.retry;

import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.core.client.config.ClientConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public class DefaultRetryPolicy implements RetryPolicy {

    private final ClientConfig clientConfig;
    private final Random random = new Random();

    public DefaultRetryPolicy(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    public boolean isRetryable(RpcResponse response) {
        if (response == null) {
            return true; // 空响应可以重试
        }

        int code = response.getCode();

        // 服务端暂时性错误(5xx)可以重试
        if (code >= 500 && code < 600) {
            return true;
        }

        // 特定错误码处理
        switch (code) {
            case 429: // 限流 - 可以重试但需要更长等待时间
                return true;
            case 400: // 错误请求
            case 401: // 未授权
            case 403: // 禁止访问
            case 404: // 未找到
            case 405: // 方法不存在
                return false; // 客户端错误不重试
            default:
                return false; // 默认不重试未知错误
        }
    }

    @Override
    public long getNextRetryDelayMs(int retryCount, RpcResponse response) {
        long baseDelay = clientConfig.getRetryIntervalMillis();

        // 对于限流错误，使用更长的延迟
        if (response != null && response.getCode() == 429) {
            baseDelay *= 2;
            log.info("遇到限流，使用更长的重试延迟: {}ms", baseDelay);
        }

        // 指数退避策略
        if (clientConfig.getBackoffMultiplier() > 1.0) {
            baseDelay *= Math.pow(clientConfig.getBackoffMultiplier(), retryCount);
        }

        // 最大退避时间限制
        baseDelay = Math.min(baseDelay, clientConfig.getMaxBackoffTime());

        // 增加随机抖动，避免重试风暴
        if (clientConfig.isAddJitter()) {
            baseDelay = (long) (baseDelay * (1.0 + random.nextDouble() * 0.2 - 0.1)); // ±10%抖动
        }

        return Math.max(baseDelay, clientConfig.getMinRetryInterval());
    }
}