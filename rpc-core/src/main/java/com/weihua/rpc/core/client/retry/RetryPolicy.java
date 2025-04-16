/*
 * @Author: weihua hu
 * @Date: 2025-04-12 16:14:16
 * @LastEditTime: 2025-04-16 22:09:33
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.retry;

import com.weihua.rpc.common.model.RpcResponse;

/**
 * 重试策略接口，决定哪些响应可以重试以及重试等待时间
 */
public interface RetryPolicy {
    /**
     * 判断响应是否可以重试
     * 
     * @param response RPC响应
     * @return 是否可以重试
     */
    boolean isRetryable(RpcResponse response);

    /**
     * 获取下一次重试的等待时间
     * 
     * @param retryCount 当前重试次数
     * @param response   上一次响应
     * @return 等待时间(毫秒)
     */
    long getNextRetryDelayMs(int retryCount, RpcResponse response);
}
