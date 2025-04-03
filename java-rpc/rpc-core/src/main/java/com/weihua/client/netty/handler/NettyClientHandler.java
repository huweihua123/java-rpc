/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:15:34
 * @LastEditTime: 2025-04-03 19:10:48
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.netty.handler;

import java.util.Map;

import com.weihua.client.util.RpcFutureManager;
import com.weihua.trace.RequestTraceContextManager;

import common.message.RpcResponse;
import common.trace.TraceContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) throws Exception {
        // 接收到response, 给channel设计别名，让sendRequest里读取response
        // AttributeKey<RpcResponse> key = AttributeKey.valueOf("RPCResponse");
        // ctx.channel().attr(key).set(response);
        if (response == null || response.getRequestId() == null) {
            log.warn("收到无效响应");
            return;
        }

        // 从管理器获取请求对应的TraceContext
        Map<String, String> traceContext = RequestTraceContextManager.getTraceContext(response.getRequestId());
        if (traceContext != null) {
            // 设置线程上下文
            TraceContext.clone(traceContext);
        }

        try {
            RpcFutureManager.completeFuture(response.getRequestId(), response);
        } finally {
            // 清理资源
            RequestTraceContextManager.removeTraceContext(response.getRequestId());
            TraceContext.clear();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel exception occurred", cause);
        ctx.close();
    }
}
