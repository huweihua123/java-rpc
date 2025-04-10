/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:21:18
 * @LastEditTime: 2025-04-10 02:21:19
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.ratelimit;

/**
 * 限流接口
 */
public interface RateLimit {

    /**
     * 尝试获取一个令牌，判断当前请求是否允许通过
     * 
     * @return 如果允许请求通过返回true，否则返回false
     */
    boolean allowRequest();

    /**
     * 获取接口名称
     * 
     * @return 接口名称
     */
    String getInterfaceName();

    /**
     * 获取最大QPS
     * 
     * @return 最大QPS
     */
    int getMaxQps();

    /**
     * 获取当前QPS
     * 
     * @return 当前QPS
     */
    double getCurrentQps();
}
