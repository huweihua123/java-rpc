/*
 * @Author: weihua hu
 * @Date: 2025-04-06 18:10:28
 * @LastEditTime: 2025-04-06 19:13:18
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.invoker;

import com.weihua.client.util.RpcFutureManager;
import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 基于Netty Channel的Invoker实现
 */
@Log4j2
public class ChannelInvoker implements Invoker {

    private final Channel channel;
    private final String id;
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder totalResponseTime = new LongAdder();
    private final int timeoutSeconds;

    public ChannelInvoker(Channel channel, int timeoutSeconds) {
        this.channel = channel;
        this.timeoutSeconds = timeoutSeconds;
        this.id = UUID.randomUUID().toString();
        log.debug("创建ChannelInvoker: {}, 超时时间: {}秒", id, timeoutSeconds);
    }

    @Override
    public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
        // 创建返回结果的Future
        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();

        if (!isAvailable()) {
            responseFuture.completeExceptionally(new IllegalStateException("Invoker不可用，连接已关闭或不活跃"));
            failedRequests.increment();
            return responseFuture;
        }

        try {
            // 记录活跃请求计数
            activeCount.incrementAndGet();
            totalRequests.increment();

            // 记录开始时间
            long startTime = System.currentTimeMillis();

            // 注册请求Future
            RpcFutureManager.putFuture(request.getRequestId(), responseFuture);

            // 发送请求
            ChannelFuture writeFuture = channel.writeAndFlush(request);

            // 添加请求发送监听器
            writeFuture.addListener(future -> {
                if (!future.isSuccess()) {
                    log.error("请求发送失败: {}", future.cause().getMessage());
                    RpcFutureManager.completeExceptionally(request.getRequestId(), future.cause());
                    failedRequests.increment();
                }
            });

            // 为Future添加完成回调，用于统计和清理
            responseFuture.whenComplete((response, throwable) -> {
                // 减少活跃请求计数
                activeCount.decrementAndGet();

                // 计算响应时间
                long responseTime = System.currentTimeMillis() - startTime;
                totalResponseTime.add(responseTime);

                if (throwable != null) {
                    failedRequests.increment();
                    log.debug("请求异常完成: {}, 耗时: {}ms, 异常: {}",
                            request.getRequestId(), responseTime, throwable.getMessage());
                } else {
                    log.debug("请求正常完成: {}, 耗时: {}ms", request.getRequestId(), responseTime);
                }
            });

            return responseFuture;
        } catch (Exception e) {
            // 请求发送异常处理
            activeCount.decrementAndGet();
            failedRequests.increment();
            RpcFutureManager.removeFuture(request.getRequestId());

            responseFuture.completeExceptionally(e);
            log.error("发送请求时发生异常: {}", e.getMessage());
            return responseFuture;
        }
    }

    @Override
    public int getActiveCount() {
        return activeCount.get();
    }

    @Override
    public InetSocketAddress getAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public boolean isAvailable() {
        return channel != null && channel.isActive() && channel.isWritable();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void destroy() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
                log.info("关闭Channel连接: {}", getAddress());
            } catch (Exception e) {
                log.error("关闭Channel时发生异常", e);
            }
        }
    }

    @Override
    public double getAvgResponseTime() {
        long count = totalRequests.sum() - failedRequests.sum();
        if (count <= 0) {
            return 0;
        }
        return (double) totalResponseTime.sum() / count;
    }

    @Override
    public double getSuccessRate() {
        long total = totalRequests.sum();
        if (total <= 0) {
            return 1.0; // 没有请求时默认为100%成功率
        }
        long failed = failedRequests.sum();
        return (double) (total - failed) / total;
    }
}