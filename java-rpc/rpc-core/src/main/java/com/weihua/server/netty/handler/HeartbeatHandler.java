package com.weihua.server.netty.handler;

import com.weihua.config.heartbeat.HeartbeatConfig;

import common.message.RequestType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端心跳处理器
 * 主要用于处理空闲状态检测和心跳响应
 */
@Log4j2
public class HeartbeatHandler extends ChannelDuplexHandler {
    // 连续失败超过此值才关闭连接
    private static final int MAX_FAILURES = 3;
    // 记录每个连接的心跳失败次数
    private final Map<String, Integer> channelFailureCounter = new ConcurrentHashMap<>();
    // 心跳配置
    private final HeartbeatConfig config;

    public HeartbeatHandler() {
        this.config = HeartbeatConfig.getInstance();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            String channelId = ctx.channel().id().asLongText();
            if (e.state() == IdleState.READER_IDLE) {
                // 长时间未收到客户端消息
                int failures = channelFailureCounter.getOrDefault(channelId, 0) + 1;

                if (failures >= MAX_FAILURES) {
                    // 连续多次心跳失败，关闭连接
                    log.warn("连续{}次无心跳，关闭连接: {}", MAX_FAILURES, ctx.channel());
                    ctx.close();
                    channelFailureCounter.remove(channelId);
                } else {
                    // 记录失败次数
                    channelFailureCounter.put(channelId, failures);
                    log.warn("心跳检测失败({}次/{}): {}", failures, MAX_FAILURES, ctx.channel());
                }
            } else if (e.state() == IdleState.WRITER_IDLE) {
                // 长时间未向客户端发送数据，发送心跳保持活跃
                log.debug("长时间未写入数据，发送服务端心跳: {}", ctx.channel());
                sendServerHeartbeat(ctx);
            }

        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    /**
     * 发送服务端心跳
     */
    private void sendServerHeartbeat(ChannelHandlerContext ctx) {
        RpcResponse heartbeat = RpcResponse.success("server-heartbeat");
        heartbeat.setRequestId("server-heartbeat");
        heartbeat.setHeartBeat(true);

        ctx.writeAndFlush(heartbeat)
                .addListener(future -> {
                    if (!future.isSuccess()) {
                        log.warn("服务端心跳发送失败: {}", future.cause().getMessage());
                    } else {
                        log.debug("服务端心跳发送成功");
                    }
                });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 收到任何消息，重置心跳失败计数
        String channelId = ctx.channel().id().asLongText();
        channelFailureCounter.put(channelId, 0);

        // 检查是否是心跳请求并处理
        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest) msg;

            // 在这里拦截心跳请求
            if (request.isHeartBeat() || request.getType() == RequestType.HEARTBEAT) {
                log.debug("HeartbeatHandler处理心跳包: {}", request.getRequestId());
                RpcResponse heartbeatResponse = RpcResponse.success("pong");
                heartbeatResponse.setRequestId(request.getRequestId());
                heartbeatResponse.setHeartBeat(true);
                ctx.writeAndFlush(heartbeatResponse);
                log.info("已被heartBeat处理");
                // 重要：不再传递心跳请求给后续处理器
                return;
            }
        }
        // 仅处理IdleStateEvent，不处理心跳请求
        // 心跳请求交给NettyServerHandler处理
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("心跳处理器异常: {}", cause.getMessage());
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接断开时清理资源
        String channelId = ctx.channel().id().asLongText();
        channelFailureCounter.remove(channelId);
        log.info("连接断开，清理心跳计数器: {}", ctx.channel());
        ctx.fireChannelInactive();
    }
}