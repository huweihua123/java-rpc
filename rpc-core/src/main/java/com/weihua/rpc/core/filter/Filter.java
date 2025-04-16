/*
 * @Author: weihua hu
 * @Date: 2025-04-16 19:34:41
 * @LastEditTime: 2025-04-16 19:34:43
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.filter;

import com.weihua.rpc.common.extension.SPI;
import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.common.exception.RpcException;

/**
 * RPC过滤器接口，用于拦截处理RPC请求
 */
@SPI
public interface Filter {

    /**
     * 处理RPC请求
     * 
     * @param request 请求信息
     * @param invoker 下一个调用者
     * @return 处理结果
     * @throws RpcException RPC调用异常
     */
    RpcResponse invoke(RpcRequest request, Invoker<?> invoker) throws RpcException;
}