/*
 * @Author: weihua hu
 * @Date: 2025-04-02 23:02:24
 * @LastEditTime: 2025-04-02 23:02:25
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.client.util;

import common.message.RpcResponse;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC请求Future管理器
 * 用于管理异步RPC请求的请求-响应映射关系
 */
@Log4j2
public class RpcFutureManager {
    // 存储所有进行中的请求 <requestId, future>
    private static final Map<String, CompletableFuture<RpcResponse>> FUTURES = new ConcurrentHashMap<>();

    // 用于超时检测的调度器
    private static final ScheduledExecutorService TIMEOUT_CHECKER = new ScheduledThreadPoolExecutor(
            1,
            r -> {
                Thread thread = new Thread(r, "rpc-future-timeout-checker");
                thread.setDaemon(true);
                return thread;
            });

    // 请求统计
    private static final AtomicInteger PENDING_REQUESTS = new AtomicInteger(0);

    // 默认超时时间（毫秒）
    private static final long DEFAULT_TIMEOUT = 5000;

    static {
        // 启动定时任务，检查超时请求
        TIMEOUT_CHECKER.scheduleAtFixedRate(
                RpcFutureManager::checkTimeoutRequests,
                1000, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * 注册新的RPC请求Future
     * 
     * @param requestId 请求ID
     * @param future    请求对应的Future对象
     */
    public static void putFuture(String requestId, CompletableFuture<RpcResponse> future) {
        FUTURES.put(requestId, future);
        PENDING_REQUESTS.incrementAndGet();
        log.debug("注册请求Future: {}, 当前待处理请求: {}", requestId, PENDING_REQUESTS.get());
    }

    /**
     * 完成指定请求的Future
     * 
     * @param requestId 请求ID
     * @param response  响应结果
     */
    public static void completeFuture(String requestId, RpcResponse response) {
        CompletableFuture<RpcResponse> future = FUTURES.remove(requestId);
        if (future != null) {
            future.complete(response);
            PENDING_REQUESTS.decrementAndGet();
            log.debug("完成请求Future: {}, 当前待处理请求: {}", requestId, PENDING_REQUESTS.get());
        } else {
            log.warn("未找到请求ID对应的Future: {}, 可能已超时或被取消", requestId);
        }
    }

    /**
     * 使指定请求的Future异常完成
     * 
     * @param requestId 请求ID
     * @param throwable 异常
     */
    public static void completeExceptionally(String requestId, Throwable throwable) {
        CompletableFuture<RpcResponse> future = FUTURES.remove(requestId);
        if (future != null) {
            future.completeExceptionally(throwable);
            PENDING_REQUESTS.decrementAndGet();
            log.debug("请求Future异常完成: {}, 异常: {}", requestId, throwable.getMessage());
        }
    }

    /**
     * 移除指定请求的Future
     * 
     * @param requestId 请求ID
     */
    public static void removeFuture(String requestId) {
        CompletableFuture<RpcResponse> future = FUTURES.remove(requestId);
        if (future != null) {
            PENDING_REQUESTS.decrementAndGet();
            log.debug("移除请求Future: {}", requestId);
        }
    }

    /**
     * 检查是否有超时请求
     */
    private static void checkTimeoutRequests() {
        long now = System.currentTimeMillis();
        FUTURES.forEach((requestId, future) -> {
            // 这里可以添加超时逻辑，例如基于请求创建时间
            // 为了实现这一点，可能需要扩展存储结构，记录每个请求的创建时间
        });
    }

    /**
     * 获取当前待处理的请求数量
     */
    public static int getPendingRequestCount() {
        return PENDING_REQUESTS.get();
    }

    /**
     * 关闭资源
     */
    public static void shutdown() {
        TIMEOUT_CHECKER.shutdown();
        for (Map.Entry<String, CompletableFuture<RpcResponse>> entry : FUTURES.entrySet()) {
            String requestId = entry.getKey();
            CompletableFuture<RpcResponse> future = entry.getValue();
            future.completeExceptionally(new IllegalStateException("RPC客户端关闭"));
            log.debug("客户端关闭，终止请求: {}", requestId);
        }
        FUTURES.clear();
    }
}