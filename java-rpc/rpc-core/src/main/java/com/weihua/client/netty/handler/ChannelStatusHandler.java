/*
 * @Author: weihua hu
 * @Date: 2025-04-06 19:09:40
 * @LastEditTime: 2025-04-06 19:09:42
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.netty.handler;

import com.weihua.client.metrics.InvokerMetricsCollector;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;

/**
 * Channel状态监控处理器
 * 监控Channel的状态变化，收集性能指标
 */
@Log4j2
public class ChannelStatusHandler extends ChannelDuplexHandler {

    private final InvokerMetricsCollector metricsCollector = InvokerMetricsCollector.getInstance();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        log.debug("Channel激活: {}", address);

        // 启动性能指标收集器（如果尚未启动）
        metricsCollector.start();

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        log.debug("Channel不活跃: {}", address);
        super.channelInactive(ctx);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        log.debug("Channel关闭: {}", address);
        super.close(ctx, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        log.warn("Channel异常: {}, 异常: {}", address, cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        // 读取数据时记录
        super.read(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // 写入数据时记录
        super.write(ctx, msg, promise);
    }
}
