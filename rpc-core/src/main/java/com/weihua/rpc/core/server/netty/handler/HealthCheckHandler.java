/*
 * @Author: weihua hu
 * @Date: 2025-04-10 20:04:40
 * @LastEditTime: 2025-04-10 20:08:27
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * 健康检查处理器
 * 用于处理来自服务注册中心的TCP健康检查连接
 */
@Slf4j
public class HealthCheckHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            // 如果是空连接或只有几个字节的连接，可能是健康检查
            if (buf.readableBytes() <= 2) {
                log.debug("收到疑似健康检查连接, 来自: {}", ctx.channel().remoteAddress());
                buf.release(); // 释放ByteBuf
                return; // 不向后传递
            }
        }
        // 不是健康检查相关的数据，传递给下一个处理器
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("健康检查处理器捕获异常, 可能是客户端断开连接", cause);
        ctx.close();
    }
}