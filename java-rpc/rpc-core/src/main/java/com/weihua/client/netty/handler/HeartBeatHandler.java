/*
 * @Author: weihua hu
 * @Date: 2025-04-02 00:31:09
 * @LastEditTime: 2025-04-04 20:57:21
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.netty.handler;

import com.weihua.client.pool.ChannelPool;
import common.message.RpcRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Log4j2
public class HeartBeatHandler extends ChannelDuplexHandler {

    private final int writerIdleTime;
    private final TimeUnit timeUnit;

    public HeartBeatHandler(int writerIdleTime, TimeUnit timeUnit) {
        this.writerIdleTime = writerIdleTime;
        this.timeUnit = timeUnit;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                log.info("超过{}{}没有写数据，发送心跳包", writerIdleTime, timeUnit.name().toLowerCase());
                sendHeartbeat(ctx);
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 当通道变为不活跃时(服务端关闭)，从连接池中移除
        removeFromPool(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("连接异常: {}", cause.getMessage());
        // 发生异常时从连接池中移除，但不主动关闭连接
        removeFromPool(ctx.channel());
        // 将异常传递给下一个处理器
        ctx.fireExceptionCaught(cause);
    }

    /**
     * 发送心跳包
     */
    private void sendHeartbeat(ChannelHandlerContext ctx) {
        RpcRequest heartbeatRequest = RpcRequest.heartBeat();
        ctx.writeAndFlush(heartbeatRequest).addListener(future -> {
            if (!future.isSuccess()) {
                log.warn("心跳包发送失败", future.cause());
            } else {
                log.debug("心跳包发送成功");
            }
        });
    }

    /**
     * 从连接池中移除通道
     */
    private void removeFromPool(Channel channel) {
        if (channel != null) {
            try {
                InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
                if (address != null) {
                    ChannelPool.getInstance().removeChannel(address, channel);
                    log.info("连接不可用，已从连接池移除：{}", address);
                }
            } catch (Exception e) {
                log.error("移除连接池中的Channel失败", e);
            }
        }
    }
}