/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:58:20
 * @LastEditTime: 2025-04-10 01:58:22
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.circuit;

/**
 * 熔断器接口
 */
public interface CircuitBreaker {

    /**
     * 判断是否允许请求通过
     * 
     * @return 如果熔断器关闭或半开状态可以尝试请求返回true，否则返回false
     */
    boolean allowRequest();

    /**
     * 记录成功调用
     */
    void recordSuccess();

    /**
     * 记录失败调用
     */
    void recordFailure();

    /**
     * 获取熔断器状态
     * 
     * @return 熔断器状态
     */
    State getState();

    /**
     * 熔断器状态枚举
     */
    enum State {
        /**
         * 关闭状态（允许请求）
         */
        CLOSED,

        /**
         * 打开状态（拒绝请求）
         */
        OPEN,

        /**
         * 半开状态（允许有限请求通过以测试系统是否恢复）
         */
        HALF_OPEN
    }
}
