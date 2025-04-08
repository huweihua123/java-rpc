/*
 * @Author: weihua hu
 * @Date: 2025-04-07 18:40:41
 * @LastEditTime: 2025-04-08 01:53:12
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.server.netty.handler;

import com.weihua.server.handler.TraceServerHandler;
import com.weihua.trace.TraceContext;
import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.extern.log4j.Log4j2;

/**
 * Netty服务端链路追踪处理器
 */
@Log4j2
public class NettyTraceHandler extends ChannelDuplexHandler {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 检查是否是RPC请求
        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest) msg;

            // 处理请求中的追踪信息，创建服务端span
            TraceContext context = TraceServerHandler.handleRequest(request);

            log.debug("服务端接收请求: traceId={}, spanId={}, 服务={}, 方法={}",
                    context.getTraceId(), context.getSpanId(),
                    context.getServiceName(), context.getMethodName());
        }

        // 继续Pipeline处理
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // 检查是否是RPC响应
        if (msg instanceof RpcResponse) {
            RpcResponse response = (RpcResponse) msg;

            // 处理响应，注入追踪信息
            TraceServerHandler.handleResponse(response);

            log.debug("服务端发送响应: traceId={}, 状态码={}",
                    response.getTraceId(), response.getCode());
        } else {
            log.info("不是RpcResponse:{}", msg);
        }

        // 继续Pipeline处理
        ctx.write(msg, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("链路追踪处理异常", cause);
        ctx.fireExceptionCaught(cause);
    }
}