/*
 * @Author: weihua hu
 * @Date: 2025-04-16 19:35:25
 * @LastEditTime: 2025-04-16 19:36:43
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.filter;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.common.exception.RpcException;

/**
 * 服务调用者接口
 */
public interface Invoker<T> {

    /**
     * 调用服务
     * 
     * @param request 请求信息
     * @return 调用结果
     * @throws RpcException RPC调用异常
     */
    RpcResponse invoke(RpcRequest request) throws RpcException;

    /**
     * 获取服务接口类
     * 
     * @return 服务接口类
     */
    Class<T> getInterface();

    /**
     * 获取服务实现对象
     * 
     * @return 服务实现对象
     */
    T getImpl();
}