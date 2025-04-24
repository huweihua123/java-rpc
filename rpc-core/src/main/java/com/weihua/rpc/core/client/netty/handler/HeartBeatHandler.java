package com.weihua.rpc.core.client.netty.handler;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端心跳处理器 - 优化版
 */
@Slf4j
public class HeartBeatHandler extends ChannelInboundHandlerAdapter {

    // 可配置参数
    private final int maxFailures;
    private final int heartbeatTimeoutSeconds;

    // 心跳统计数据
    private final AtomicLong heartbeatsSent = new AtomicLong(0);
    private final AtomicLong heartbeatsReceived = new AtomicLong(0);
    private final AtomicLong heartbeatsTotalLatency = new AtomicLong(0);
    private final AtomicLong heartbeatsTimeout = new AtomicLong(0);

    private final AtomicInteger heartbeatFailures = new AtomicInteger(0);

    // 最近的心跳请求ID和超时任务
    private volatile String lastHeartbeatId = null;
    private volatile ScheduledFuture<?> currentTimeoutTask = null;
    private volatile long lastHeartbeatTime = 0;

    // 构造方法 - 支持默认值和外部配置
    public HeartBeatHandler() {
        this(3, 3); // 默认值：3次失败，3秒超时
    }

    public HeartBeatHandler(int maxFailures, int heartbeatTimeoutSeconds) {
        this.maxFailures = maxFailures;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            // 明确区分空闲事件类型
            if (event.state() == IdleState.WRITER_IDLE) {
                // 写空闲 - 发送心跳
                sendHeartbeat(ctx);
            } else if (event.state() == IdleState.READER_IDLE) {
                // 读空闲 - 服务端可能故障
                log.warn("长时间未收到服务端消息，可能连接异常: {}", ctx.channel().remoteAddress());
                handleHeartbeatFailure(ctx);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 发送心跳请求
     */
    private void sendHeartbeat(ChannelHandlerContext ctx) {
        // 取消之前未完成的超时任务
        cancelCurrentTimeoutTask();

        // 创建心跳请求并添加元数据
        long timestamp = System.currentTimeMillis();

        RpcRequest heartbeat = RpcRequest.builder()
                .requestId("heartbeat-" + timestamp)
                .requestType(RpcRequest.RequestType.HEARTBEAT)
                .build();

        String requestId = heartbeat.getRequestId();
        lastHeartbeatId = requestId;
        lastHeartbeatTime = timestamp;

        if (log.isDebugEnabled()) {
            log.debug("发送心跳请求: {}", requestId);
        }

        // 设置超时检测
        currentTimeoutTask = ctx.executor().schedule(() -> {
            // 仅处理当前心跳的超时
            if (requestId.equals(lastHeartbeatId)) {
                log.warn("心跳请求超时: {}", requestId);
                heartbeatsTimeout.incrementAndGet();
                handleHeartbeatFailure(ctx);
                lastHeartbeatId = null;
                currentTimeoutTask = null;
            }
        }, heartbeatTimeoutSeconds, TimeUnit.SECONDS);

        // 发送心跳请求
        ctx.channel().writeAndFlush(heartbeat).addListener(future -> {
            if (future.isSuccess()) {
                heartbeatsSent.incrementAndGet();
            } else {
                // 发送失败，取消超时任务
                cancelCurrentTimeoutTask();
                log.warn("心跳请求发送失败: {}", future.cause().getMessage());
                handleHeartbeatFailure(ctx);
            }
        });
    }

    /**
     * 取消当前超时任务
     */
    private void cancelCurrentTimeoutTask() {
        ScheduledFuture<?> task = currentTimeoutTask;
        if (task != null && !task.isDone()) {
            task.cancel(false);
            currentTimeoutTask = null;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            // 收到任何业务响应都表明连接正常，重置失败计数
            if (ctx.channel().isActive()) {
                heartbeatFailures.set(0);
            }

            // 特定心跳响应处理
            if (msg instanceof RpcResponse) {
                RpcResponse response = (RpcResponse) msg;

                // 识别心跳响应
                if (response.getResponseType() == RpcResponse.ResponseType.HEARTBEAT) {
                    if (log.isDebugEnabled()) {
                        log.debug("收到心跳响应: {}", response.getRequestId());
                    }

                    heartbeatsReceived.incrementAndGet();

                    // 计算心跳延迟
                    if (lastHeartbeatTime > 0) {
                        long latency = System.currentTimeMillis() - lastHeartbeatTime;
                        heartbeatsTotalLatency.addAndGet(latency);

                        if (log.isDebugEnabled()) {
                            log.debug("心跳延迟: {}ms", latency);
                        }
                    }

                    // 收到响应表示心跳成功，取消当前超时任务
                    if (response.getRequestId() != null &&
                            response.getRequestId().equals(lastHeartbeatId)) {
                        cancelCurrentTimeoutTask();
                        lastHeartbeatId = null;
                    }

                    return; // 不再继续传递
                }
            }

            // 不是心跳响应，传递给下一个处理器
            super.channelRead(ctx, msg);
        } catch (Exception e) {
            log.error("处理心跳响应异常", e);
            super.channelRead(ctx, msg);
        }
    }

    /**
     * 处理心跳失败
     */
    private void handleHeartbeatFailure(ChannelHandlerContext ctx) {
        int failures = heartbeatFailures.incrementAndGet();
        if (failures >= maxFailures) {
            log.warn("连续{}次心跳失败，关闭连接: {}", failures, ctx.channel().remoteAddress());
            ctx.close();
        } else {
            log.warn("心跳失败 ({}/{}), 连接: {}", failures, maxFailures, ctx.channel().remoteAddress());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("心跳处理器异常: {}", cause.getMessage());
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接断开时清理资源
        cancelCurrentTimeoutTask();
        lastHeartbeatId = null;

        if (log.isDebugEnabled()) {
            log.debug("连接已断开，清理心跳资源: {}", ctx.channel().remoteAddress());
        }
        super.channelInactive(ctx);
    }

    /**
     * 获取心跳统计信息
     */
    public Map<String, Long> getHeartbeatStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("sent", heartbeatsSent.get());
        stats.put("received", heartbeatsReceived.get());
        stats.put("timeouts", heartbeatsTimeout.get());

        long avgLatency = 0;
        if (heartbeatsReceived.get() > 0) {
            avgLatency = heartbeatsTotalLatency.get() / heartbeatsReceived.get();
        }
        stats.put("avgLatencyMs", avgLatency);

        return stats;
    }
}