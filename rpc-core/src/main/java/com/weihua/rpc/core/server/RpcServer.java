/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:22:30
 * @LastEditTime: 2025-04-10 02:22:31
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server;

/**
 * RPC服务器接口
 */
public interface RpcServer {

    /**
     * 启动服务器
     * 
     * @throws Exception 启动异常
     */
    void start() throws Exception;

    /**
     * 关闭服务器
     */
    void stop();

    /**
     * 获取服务器状态
     * 
     * @return 如果服务器正在运行返回true，否则返回false
     */
    boolean isRunning();
}
