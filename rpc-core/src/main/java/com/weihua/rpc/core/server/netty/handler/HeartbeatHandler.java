package com.weihua.rpc.core.server.netty.handler;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端心跳处理器 - 优化版
 */
@Slf4j
public class HeartbeatHandler extends ChannelDuplexHandler {

    // 可配置参数
    private final int maxFailures;

    // 心跳统计
    private final AtomicLong heartbeatsSent = new AtomicLong(0);
    private final AtomicLong heartbeatsReceived = new AtomicLong(0);

    // 记录每个连接的心跳失败次数 - 使用channelId作为key
    private final Map<String, AtomicInteger> channelFailureCounter = new ConcurrentHashMap<>();

    // 记录最后一次活动时间
    private final Map<String, Long> lastActivityTime = new ConcurrentHashMap<>();

    // 构造方法 - 支持默认值和外部配置
    public HeartbeatHandler() {
        this(2); // 默认3次失败
    }

    public HeartbeatHandler(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            String channelId = ctx.channel().id().asLongText();

            if (e.state() == IdleState.READER_IDLE) {
                // 长时间未收到客户端消息
                int failures = getFailureCounter(channelId).incrementAndGet();

                if (failures >= maxFailures) {
                    // 连续多次心跳失败，关闭连接
                    log.warn("连续{}次无心跳，关闭连接: {}", maxFailures, ctx.channel());
                    ctx.close();
                    channelFailureCounter.remove(channelId);
                    lastActivityTime.remove(channelId);
                } else {
                    log.warn("心跳检测失败({}次/{}): {}", failures, maxFailures, ctx.channel());
                }
            } else if (e.state() == IdleState.WRITER_IDLE) {
                // 长时间未向客户端发送数据，发送心跳保持活跃
                if (log.isDebugEnabled()) {
                    log.debug("长时间未写入数据，发送服务端心跳: {}", ctx.channel());
                }
                sendServerHeartbeat(ctx);
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    /**
     * 发送服务端心跳 - 简化版，移除extData
     */
    private void sendServerHeartbeat(ChannelHandlerContext ctx) {
        long timestamp = System.currentTimeMillis();

        RpcResponse heartbeat = RpcResponse.builder()
                .requestId("server-heartbeat-" + timestamp)
                .code(200)
                .message("server-heartbeat")
                .responseType(RpcResponse.ResponseType.HEARTBEAT)
                // 移除extData
                .build();

        ctx.writeAndFlush(heartbeat)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        heartbeatsSent.incrementAndGet();
                        if (log.isDebugEnabled()) {
                            log.debug("服务端心跳发送成功");
                        }
                    } else if (log.isWarnEnabled()) {
                        log.warn("服务端心跳发送失败: {}", future.cause().getMessage());
                    }
                });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            String channelId = ctx.channel().id().asLongText();

            // 更新最后活动时间
            lastActivityTime.put(channelId, System.currentTimeMillis());

            // 收到任何消息，重置心跳失败计数
            if (ctx.channel().isActive()) {
                resetFailureCounter(channelId);
            }

            // 检查是否是心跳请求并处理
            if (msg instanceof RpcRequest) {
                RpcRequest request = (RpcRequest) msg;

                if (request.isHeartBeat()) {
                    heartbeatsReceived.incrementAndGet();

                    if (log.isDebugEnabled()) {
                        log.debug("处理心跳请求: {}", request.getRequestId());
                    }

                    // 简化版心跳响应，移除extData
                    RpcResponse heartbeatResponse = RpcResponse.builder()
                            .requestId(request.getRequestId())
                            .code(200)
                            .message("pong")
                            .responseType(RpcResponse.ResponseType.HEARTBEAT)
                            // 移除extData
                            .build();

                    ctx.writeAndFlush(heartbeatResponse);
                    return; // 不再传递心跳请求
                }
            }

            // 非心跳消息，传递给下一个处理器
            ctx.fireChannelRead(msg);
        } catch (Exception e) {
            log.error("处理消息异常", e);
            ctx.fireChannelRead(msg); // 确保消息继续传递
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
        String channelId = ctx.channel().id().asLongText();
        channelFailureCounter.remove(channelId);
        lastActivityTime.remove(channelId);
        ctx.fireChannelInactive();
    }

    /**
     * 获取失败计数器
     */
    private AtomicInteger getFailureCounter(String channelId) {
        return channelFailureCounter.computeIfAbsent(channelId, k -> new AtomicInteger(0));
    }

    /**
     * 重置失败计数器
     */
    private void resetFailureCounter(String channelId) {
        AtomicInteger counter = channelFailureCounter.get(channelId);
        if (counter != null) {
            counter.set(0);
        }
    }

    /**
     * 定期清理无效的连接计数器
     * 此方法可由外部定时任务调用
     */
    public void cleanupStaleCounters() {
        long now = System.currentTimeMillis();
        long staleThreshold = 5 * 60 * 1000; // 5分钟无活动视为过期

        channelFailureCounter.entrySet().removeIf(entry -> {
            String channelId = entry.getKey();
            Long lastActive = lastActivityTime.get(channelId);

            // 如果没有活动记录或者活动时间过旧，移除计数器
            if (lastActive == null || (now - lastActive > staleThreshold)) {
                lastActivityTime.remove(channelId);
                return true; // 移除此项
            }
            return false;
        });
    }

    /**
     * 获取心跳统计信息
     */
    public Map<String, Object> getHeartbeatStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sent", heartbeatsSent.get());
        stats.put("received", heartbeatsReceived.get());
        stats.put("activeConnections", channelFailureCounter.size());
        return stats;
    }
}