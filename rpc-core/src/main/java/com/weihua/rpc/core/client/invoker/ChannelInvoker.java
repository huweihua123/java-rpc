package com.weihua.rpc.core.client.invoker;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Channel的调用者实现
 */
@Slf4j
public class ChannelInvoker implements Invoker {

    private final Channel channel;
    private final String id;
    private final InetSocketAddress address;

    // 性能指标
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    public ChannelInvoker(Channel channel) {
        this.channel = channel;
        this.address = (InetSocketAddress) channel.remoteAddress();
        this.id = UUID.randomUUID().toString();

        // 添加关闭监听器，在连接关闭时清理资源
        channel.closeFuture().addListener(future -> {
            log.info("Channel已关闭: {}", address);
        });
    }

    @Override
    public long getRequestCount() {
        return totalCalls.get();
    }

    @Override
    public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
        // 创建响应Future
        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();

        try {
            // 记录开始时间
            long startTime = System.currentTimeMillis();

            // 激活计数器+1
            activeCount.incrementAndGet();

            // 请求总数+1
            totalCalls.incrementAndGet();

            // 注册Future
            RpcFutureManager.putFuture(request.getRequestId(), responseFuture);

            // 发送请求并添加监听器
            channel.writeAndFlush(request).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    // 发送失败
                    Throwable cause = future.cause();
                    log.error("发送请求失败: {}", cause.getMessage());
                    RpcFutureManager.removeFuture(request.getRequestId());
                    responseFuture.completeExceptionally(cause);

                    // 更新性能指标
                    updateMetrics(false, System.currentTimeMillis() - startTime);
                }
                // 发送成功的情况下，会在收到响应时完成future
            });

            // 添加回调，用于更新性能指标
            responseFuture.whenComplete((response, throwable) -> {
                // 激活计数器-1
                activeCount.decrementAndGet();

                if (throwable != null) {
                    log.error("调用异常: {}", throwable.getMessage());
                    updateMetrics(false, System.currentTimeMillis() - startTime);
                } else {
                    boolean success = response != null && response.getCode() == 200;
                    updateMetrics(success, System.currentTimeMillis() - startTime);
                }
            });

            return responseFuture;
        } catch (Exception e) {
            log.error("创建调用异常: {}", e.getMessage(), e);
            RpcFutureManager.removeFuture(request.getRequestId());
            responseFuture.completeExceptionally(e);

            // 激活计数器-1
            activeCount.decrementAndGet();

            return responseFuture;
        }
    }

    /**
     * 更新性能指标
     */
    private void updateMetrics(boolean success, long responseTimeMs) {
        if (success) {
            totalSuccesses.incrementAndGet();
        }
        totalResponseTime.addAndGet(responseTimeMs);
    }

    @Override
    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return channel != null && channel.isActive() && channel.isWritable();
    }

    @Override
    public int getActiveCount() {
        return activeCount.get();
    }

    @Override
    public double getAvgResponseTime() {
        long count = totalCalls.get();
        if (count <= 0) {
            return 0;
        }
        return (double) totalResponseTime.get() / count;
    }

    @Override
    public double getSuccessRate() {
        long total = totalCalls.get();
        if (total <= 0) {
            return 1.0; // 默认为100%成功率
        }
        return (double) totalSuccesses.get() / total;
    }

    @Override
    public void destroy() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close().sync();
                log.info("关闭Channel连接: {}", address);
            } catch (InterruptedException e) {
                log.error("关闭Channel时发生中断异常", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("关闭Channel时发生异常: {}", e.getMessage(), e);
            }
        }
    }
}
