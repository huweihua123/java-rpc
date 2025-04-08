/*
 * @Author: weihua hu
 * @Date: 2025-04-01 21:25:54
 * @LastEditTime: 2025-04-02 17:36:37
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.message;

/**
 * RPC请求类型枚举
 */
public enum RequestType {
    /**
     * 普通业务请求
     */
    NORMAL,

    /**
     * 心跳请求
     */
    HEARTBEAT,

    /**
     * 服务注册请求
     */
    REGISTER,

    /**
     * 服务订阅请求
     */
    SUBSCRIBE
}