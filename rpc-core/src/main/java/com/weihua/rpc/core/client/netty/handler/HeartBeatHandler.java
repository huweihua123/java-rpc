/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:54:55
 * @LastEditTime: 2025-04-10 01:54:56
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.netty.handler;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 心跳处理器
 */
@Slf4j
public class HeartBeatHandler extends ChannelInboundHandlerAdapter {

    private final AtomicInteger heartbeatFailures = new AtomicInteger(0);
    private static final int MAX_FAILURES = 3; // 最大连续失败次数

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 发送心跳请求
            sendHeartbeat(ctx);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 发送心跳请求
     */
    private void sendHeartbeat(ChannelHandlerContext ctx) {
        RpcRequest heartbeat = createHeartbeatRequest();
        log.debug("发送心跳请求: {}", heartbeat.getRequestId());

        ctx.channel().writeAndFlush(heartbeat).addListener(future -> {
            if (!future.isSuccess()) {
                log.warn("心跳请求发送失败: {}", future.cause().getMessage());
                handleHeartbeatFailure(ctx);
            }
        });
    }

    /**
     * 创建心跳请求
     */
    private RpcRequest createHeartbeatRequest() {
        return RpcRequest.builder()
                .requestId("heartbeat-" + System.currentTimeMillis())
                .build();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RpcResponse) {
            RpcResponse response = (RpcResponse) msg;
            // 检查是否是心跳响应
            if (isHeartBeatResponse(response)) {
                log.debug("收到心跳响应: {}", response.getRequestId());
                // 重置失败计数
                heartbeatFailures.set(0);
                return;
            }
        }
        // 不是心跳响应，传递给下一个处理器
        super.channelRead(ctx, msg);
    }

    /**
     * 处理心跳失败
     */
    private void handleHeartbeatFailure(ChannelHandlerContext ctx) {
        int failures = heartbeatFailures.incrementAndGet();
        if (failures >= MAX_FAILURES) {
            log.warn("连续{}次心跳失败，关闭连接", failures);
            ctx.close();
        } else {
            log.warn("心跳失败 ({}/{})", failures, MAX_FAILURES);
        }
    }

    /**
     * 判断是否是心跳响应
     */
    private boolean isHeartBeatResponse(RpcResponse response) {
        return "pong".equals(response.getMessage()) ||
                (response.getRequestId() != null && response.getRequestId().startsWith("heartbeat-"));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("心跳处理器异常", cause);
        ctx.fireExceptionCaught(cause);
    }
}
