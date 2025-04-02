/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:15:34
 * @LastEditTime: 2025-04-02 23:54:42
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.netty.handler;

import com.weihua.client.util.RpcFutureManager;

import common.message.RpcResponse;
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
        RpcFutureManager.completeFuture(response.getRequestId(), response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel exception occurred", cause);
        ctx.close();
    }
}
