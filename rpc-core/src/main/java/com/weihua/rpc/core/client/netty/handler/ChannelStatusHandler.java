/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:55:12
 * @LastEditTime: 2025-04-10 01:55:14
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.netty.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;

/**
 * 通道状态监控处理器
 */
@Slf4j
public class ChannelStatusHandler extends ChannelDuplexHandler {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Channel registered: {}", ctx.channel());
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Channel unregistered: {}", ctx.channel());
        }
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel active: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel inactive: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        log.info("Connecting to: {}", remoteAddress);
        super.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        log.info("Disconnecting from: {}", ctx.channel().remoteAddress());
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        log.info("Closing connection to: {}", ctx.channel().remoteAddress());
        super.close(ctx, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception on channel: {}", ctx.channel().remoteAddress(), cause);
        super.exceptionCaught(ctx, cause);
    }
}
