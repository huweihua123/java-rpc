/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:54:36
 * @LastEditTime: 2025-04-10 01:54:37
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.netty.handler;

import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.core.client.invoker.RpcFutureManager;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty客户端处理器
 */
@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        if (response == null || response.getRequestId() == null) {
            log.warn("收到无效响应");
            return;
        }

        // 心跳响应处理
        if (isHeartBeatResponse(response)) {
            log.debug("检测到心跳响应，传递给心跳处理器: {}", response.getRequestId());
            ctx.fireChannelRead(response);
            return;
        }

        // 记录性能指标
        if (response.getCode() == 200) {
            log.debug("请求成功完成: {}, 响应码: {}", response.getRequestId(), response.getCode());
        } else {
            log.warn("请求返回错误: {}, 响应码: {}, 消息: {}",
                    response.getRequestId(), response.getCode(), response.getMessage());
        }

        // 完成Future
        RpcFutureManager.completeFuture(response.getRequestId(), response);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel连接建立: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel连接断开: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel发生异常: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    /**
     * 判断是否是心跳响应
     */
    private boolean isHeartBeatResponse(RpcResponse response) {
        return "pong".equals(response.getMessage()) ||
                (response.getRequestId() != null && response.getRequestId().startsWith("heartbeat-"));
    }
}
