/*
 * @Author: weihua hu
 * @Date: 2025-04-15 05:25:14
 * @LastEditTime: 2025-04-15 16:50:00
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit;

import com.weihua.rpc.common.extension.SPI;
import com.weihua.rpc.core.server.annotation.RateLimit.Strategy;

/**
 * 限流器接口，支持SPI扩展
 */
@SPI("tokenBucket")
public interface RateLimit {

    /**
     * 尝试获取一个请求的执行权限
     * 
     * @return 是否允许请求通过
     */
    boolean tryAcquire();

    /**
     * 获取总请求数
     * 
     * @return 请求计数
     */
    long getRequestCount();

    /**
     * 获取被拒绝的请求数
     * 
     * @return 拒绝计数
     */
    long getRejectCount();

    /**
     * 重置统计数据
     */
    void resetStatistics();

    /**
     * 更新QPS限制
     * 
     * @param qps 新的QPS值
     */
    void updateQps(int qps);

    /**
     * 获取当前QPS配置
     * 
     * @return 当前QPS值
     */
    int getQps();

    /**
     * 获取当前使用的限流策略
     * 
     * @return 限流策略
     */
    Strategy getStrategy();
}
