/*
 * @Author: weihua hu
 * @Date: 2025-04-02 00:31:09
 * @LastEditTime: 2025-04-06 22:12:53
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.netty.handler;

import com.weihua.client.metrics.HeartbeatStats;
import com.weihua.client.pool.InvokerManager;
import com.weihua.config.heartbeat.HeartbeatConfig;

import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class HeartBeatHandler extends ChannelDuplexHandler {
    // 配置
    private final HeartbeatConfig config;

    // 心跳统计
    private final Map<Channel, HeartbeatStats> heartbeatStatsMap = new ConcurrentHashMap<>();

    // 连接管理器
    private final InvokerManager invokerManager;

    // 重连计划任务执行器
    private static final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "heartbeat-reconnect-thread");
        t.setDaemon(true);
        return t;
    });

    // 心跳失败次数计数器
    private final Map<Channel, AtomicInteger> failureCounters = new ConcurrentHashMap<>();

    // 心跳请求ID到上下文的映射
    private final Map<String, ChannelHandlerContext> heartbeatRequests = new ConcurrentHashMap<>();

    // 心跳请求编号
    private final AtomicInteger heartbeatCounter = new AtomicInteger(0);

    // 构造方法，现在使用全局配置
    public HeartBeatHandler() {
        this.config = HeartbeatConfig.getInstance();
        this.invokerManager = InvokerManager.getInstance();

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                log.debug("超过{}{}没有写数据，发送心跳包",
                        config.getWriterIdleTime(),
                        config.getTimeUnit().name().toLowerCase());
                sendHeartbeat(ctx);
            } else if (event.state() == IdleState.READER_IDLE) {
                log.debug("超过{}{}没有读取数据，检查连接",
                        config.getReaderIdleTime(),
                        config.getTimeUnit().name().toLowerCase());

                checkConnection(ctx);
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RpcResponse) {
            RpcResponse response = (RpcResponse) msg;

            // 检查是否是心跳响应
            if (response.isHeartBeat()) {
                String requestId = response.getRequestId();
                log.debug("收到心跳响应: {}", requestId);

                // 移除心跳请求记录
                ChannelHandlerContext heartbeatCtx = heartbeatRequests.remove(requestId);
                if (heartbeatCtx != null) {
                    // 重置失败计数
                    resetFailureCounter(ctx.channel());

                    // 更新心跳统计
                    updateHeartbeatStats(ctx.channel(), true);

                    // 不再继续传递心跳响应
                    return;
                }
            }
        }

        // 非心跳消息或未找到对应请求，则传递给下一个处理器
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 添加通道到统计系统
        Channel channel = ctx.channel();
        heartbeatStatsMap.putIfAbsent(channel, new HeartbeatStats());
        resetFailureCounter(channel);

        log.info("通道激活: {}", ctx.channel());

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();

        log.info("通道不活跃: {}", address);

        // 清理资源
        heartbeatStatsMap.remove(channel);
        failureCounters.remove(channel);

        // 处理自动重连
        if (config.isAutoReconnect() && address != null) {
            scheduleReconnect(address.getHostName() + ":" + address.getPort(), 0);
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("连接异常: {}", cause.getMessage());

        // 更新心跳统计
        updateHeartbeatStats(ctx.channel(), false);

        // 传递异常事件
        ctx.fireExceptionCaught(cause);
    }

    /**
     * 发送心跳请求
     */
    private void sendHeartbeat(ChannelHandlerContext ctx) {

        // 先检查通道状态
        if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
            log.debug("通道已关闭或不活跃，取消发送心跳");
            return;
        }

        // 添加这个检查，避免向同一个通道发送过多心跳
        Channel channel = ctx.channel();
        if (heartbeatStatsMap.containsKey(channel) &&
                System.currentTimeMillis() - heartbeatStatsMap.get(channel).getLastSuccessTime() < 5000) {
            log.debug("上次心跳成功时间距今不到5秒，跳过本次心跳");
            return;
        }
        // 生成心跳ID，格式为"heartbeat-序号"
        String heartbeatId = "heartbeat-" + heartbeatCounter.incrementAndGet();

        // 创建心跳请求
        RpcRequest heartbeatRequest = RpcRequest.heartBeat();
        heartbeatRequest.setRequestId(heartbeatId);

        // 记录心跳请求
        heartbeatRequests.put(heartbeatId, ctx);

        // 设置超时任务
        scheduleHeartbeatTimeout(heartbeatId, ctx);

        // 发送心跳
        ctx.writeAndFlush(heartbeatRequest).addListener(future -> {
            if (!future.isSuccess()) {
                log.warn("心跳包发送失败", future.cause());
                heartbeatRequests.remove(heartbeatId);
                handleHeartbeatFailure(ctx);
            } else {
                log.debug("心跳包发送成功: {}", heartbeatId);
            }
        });
    }

    /**
     * 设置心跳响应超时任务
     */
    private void scheduleHeartbeatTimeout(String heartbeatId, ChannelHandlerContext ctx) {
        reconnectExecutor.schedule(() -> {
            if (heartbeatRequests.remove(heartbeatId) != null) {
                // 心跳请求超时
                log.warn("心跳请求超时: {}", heartbeatId);
                handleHeartbeatFailure(ctx);
            }
        }, config.getHeartbeatResponseTimeout(), TimeUnit.SECONDS);
    }

    /**
     * 处理心跳失败
     */
    private void handleHeartbeatFailure(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();

        // 更新心跳统计
        updateHeartbeatStats(channel, false);

        // 增加失败计数
        AtomicInteger counter = failureCounters.get(channel);
        if (counter != null) {
            int failures = counter.incrementAndGet();
            log.warn("心跳失败次数: {}/{}", failures, config.getMaxHeartbeatFailures());

            // 超过最大失败次数，关闭连接
            if (failures >= config.getMaxHeartbeatFailures()) {
                log.error("连续{}次心跳失败，关闭连接: {}",
                        failures, channel.remoteAddress());
                handleInactiveChannel(channel);
                channel.close();
            }
        }
    }

    /**
     * 检查连接状态
     */
    private void checkConnection(ChannelHandlerContext ctx) {
        // 发送心跳来检查连接
        sendHeartbeat(ctx);
    }

    /**
     * 处理不活跃的通道
     */
    private void handleInactiveChannel(Channel channel) {
        if (channel != null) {
            try {
                InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
                if (address != null) {
                    // 记录日志
                    log.info("连接不可用: {}, 将在连接维护周期内被自动清理", address);
                }
            } catch (Exception e) {
                log.error("处理不可用连接时发生异常", e);
            }
        }
    }

    /**
     * 重置失败计数器
     */
    private void resetFailureCounter(Channel channel) {
        failureCounters.put(channel, new AtomicInteger(0));
    }

    /**
     * 更新心跳统计信息
     */
    private void updateHeartbeatStats(Channel channel, boolean success) {
        HeartbeatStats stats = heartbeatStatsMap.get(channel);
        if (stats != null) {
            if (success) {
                stats.recordSuccess();
            } else {
                stats.recordFailure();
            }
        }
    }

    /**
     * 安排重连任务
     */
    private void scheduleReconnect(String address, int attemptCount) {
        if (attemptCount >= config.getMaxReconnectAttempts()) {
            log.warn("已达到最大重连尝试次数({}), 放弃重连: {}",
                    config.getMaxReconnectAttempts(), address);
            return;
        }

        // 计算重连延迟时间(使用指数退避策略)
        long delay = Math.min(
                config.getInitialReconnectDelay() *
                        Math.round(Math.pow(config.getReconnectBackoffMultiplier(), attemptCount)),
                config.getMaxReconnectDelay());

        // 安排重连任务
        reconnectExecutor.schedule(() -> {
            try {
                log.info("尝试重连(第{}次): {}", attemptCount + 1, address);

                // 尝试重新获取连接
                invokerManager.getInvoker(address);
                log.info("重连成功: {}", address);
            } catch (Exception e) {
                log.warn("重连失败: {}, 原因: {}", address, e.getMessage());

                // 继续安排下一次重连
                scheduleReconnect(address, attemptCount + 1);
            }
        }, delay, TimeUnit.MILLISECONDS);

        log.info("安排第{}次重连, 延迟: {}ms, 地址: {}",
                attemptCount + 1, delay, address);
    }

    /**
     * 获取心跳统计信息
     */
    public HeartbeatStats getHeartbeatStats(Channel channel) {
        return heartbeatStatsMap.get(channel);
    }
}