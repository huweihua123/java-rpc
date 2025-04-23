package com.weihua.rpc.core.protocol;

import com.weihua.rpc.common.model.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
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
@Slf4j
public class RpcFutureManager {
    // 存储所有进行中的请求 <requestId, RequestContext>
    private static final Map<String, RequestContext> FUTURES = new ConcurrentHashMap<>();

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

    // 默认超时时间
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    // 超时检查间隔
    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(1);

    static {
        // 启动定时任务，检查超时请求
        TIMEOUT_CHECKER.scheduleAtFixedRate(
                RpcFutureManager::checkTimeoutRequests,
                CHECK_INTERVAL.toMillis(),
                CHECK_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 请求上下文类，包含Future和时间戳
     */
    private static class RequestContext {
        final CompletableFuture<RpcResponse> future;
        final Instant createTime;

        RequestContext(CompletableFuture<RpcResponse> future) {
            this.future = future;
            this.createTime = Instant.now();
        }
    }

    /**
     * 注册新的RPC请求Future
     * 
     * @param requestId 请求ID
     * @param future    请求对应的Future对象
     */
    public static void putFuture(String requestId, CompletableFuture<RpcResponse> future) {
        FUTURES.put(requestId, new RequestContext(future));
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
        RequestContext context = FUTURES.remove(requestId);
        if (context != null) {
            context.future.complete(response);
            PENDING_REQUESTS.decrementAndGet();
            Duration responseTime = Duration.between(context.createTime, Instant.now());
            log.debug("完成请求Future: {}, 响应时间: {}ms, 当前待处理请求: {}",
                    requestId, responseTime.toMillis(), PENDING_REQUESTS.get());
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
        RequestContext context = FUTURES.remove(requestId);
        if (context != null) {
            context.future.completeExceptionally(throwable);
            PENDING_REQUESTS.decrementAndGet();
            Duration responseTime = Duration.between(context.createTime, Instant.now());
            log.debug("请求Future异常完成: {}, 响应时间: {}ms, 异常: {}",
                    requestId, responseTime.toMillis(), throwable.getMessage());
        }
    }

    /**
     * 移除指定请求的Future
     * 
     * @param requestId 请求ID
     */
    public static void removeFuture(String requestId) {
        RequestContext context = FUTURES.remove(requestId);
        if (context != null) {
            PENDING_REQUESTS.decrementAndGet();
            log.debug("移除请求Future: {}", requestId);
        }
    }

    /**
     * 检查是否有超时请求
     */
    private static void checkTimeoutRequests() {
        Instant now = Instant.now();

        // 遍历检查所有进行中的请求
        FUTURES.forEach((requestId, context) -> {
            Duration elapsedTime = Duration.between(context.createTime, now);
            if (elapsedTime.compareTo(DEFAULT_TIMEOUT) > 0) {
                // 超时处理
                log.warn("请求超时: {}, 已经过 {}ms", requestId, elapsedTime.toMillis());
                completeExceptionally(requestId,
                        new TimeoutException("请求超时，已等待" + elapsedTime.toMillis() + "ms"));
            }
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
        for (Map.Entry<String, RequestContext> entry : FUTURES.entrySet()) {
            String requestId = entry.getKey();
            RequestContext context = entry.getValue();
            context.future.completeExceptionally(new IllegalStateException("RPC客户端关闭"));
            log.debug("客户端关闭，终止请求: {}", requestId);
        }
        FUTURES.clear();
    }

    /**
     * 自定义超时异常
     */
    public static class TimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TimeoutException(String message) {
            super(message);
        }
    }
}