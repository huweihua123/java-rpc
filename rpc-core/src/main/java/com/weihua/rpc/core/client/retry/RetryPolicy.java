/*
 * @Author: weihua hu
 * @Date: 2025-04-22 18:18:17
 * @LastEditTime: 2025-04-23 15:58:56
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.retry;

import com.weihua.rpc.common.model.RpcResponse;

import java.time.Duration;

/**
 * 重试策略接口
 */
public interface RetryPolicy {
    
    /**
     * 判断响应是否可以重试
     * 
     * @param response 响应对象
     * @return 是否可重试
     */
    boolean isRetryable(RpcResponse response);
    
    /**
     * 获取下一次重试延迟时间
     * 
     * @param retryCount 当前重试次数
     * @param response 上一次响应
     * @return 下一次重试延迟时间
     */
    Duration getNextRetryDelay(int retryCount, RpcResponse response);
    
    /**
     * 获取下一次重试延迟时间（毫秒）
     * 
     * @param retryCount 当前重试次数
     * @param response 上一次响应
     * @return 下一次重试延迟时间（毫秒）
     * @deprecated 请使用 {@link #getNextRetryDelay(int, RpcResponse)} 代替
     */
    @Deprecated
    default long getNextRetryDelayMs(int retryCount, RpcResponse response) {
        return getNextRetryDelay(retryCount, response).toMillis();
    }
}