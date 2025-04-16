package com.weihua.rpc.core.server.fallback.impl;

import com.weihua.rpc.common.exception.RateLimitException;
import com.weihua.rpc.core.server.annotation.RateLimit.FallbackStrategy;
import com.weihua.rpc.core.server.fallback.AbstractFallbackHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 请求排队的降级处理器
 */
@Slf4j
public class QueueFallbackHandler extends AbstractFallbackHandler {
    
    // 资源请求队列，key为资源名称，value为该资源的请求队列
    private final ConcurrentHashMap<String, BlockingQueue<RequestTask>> requestQueues = new ConcurrentHashMap<>();
    
    // 执行队列中请求的线程池
    private final ExecutorService executorService;
    
    // 默认队列容量
    private final int defaultQueueCapacity;
    
    // 默认请求等待超时时间（毫秒）
    private final long defaultTimeoutMs;
    
    public QueueFallbackHandler() {
        this(100, 1000); // 默认100容量，1秒超时
    }
    
    public QueueFallbackHandler(int queueCapacity, long timeoutMs) {
        super(FallbackStrategy.QUEUE);
        this.defaultQueueCapacity = queueCapacity;
        this.defaultTimeoutMs = timeoutMs;
        
        int processors = Runtime.getRuntime().availableProcessors();
        this.executorService = new ThreadPoolExecutor(
                processors, // 核心线程数
                processors * 2, // 最大线程数
                60, TimeUnit.SECONDS, // 空闲线程存活时间
                new LinkedBlockingQueue<>(1000), // 任务队列
                new ThreadFactory() {
                    private final ThreadGroup group = new ThreadGroup("rate-limit-queue-group");
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(group, r, "rate-limit-queue-thread-" + threadNumber.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
        
        // 启动一个守护线程定期清理空队列
        startCleanupThread();
        
        log.info("创建请求排队降级处理器: 队列容量={}, 超时时间={}ms", defaultQueueCapacity, defaultTimeoutMs);
    }
    
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 每分钟清理一次
                    Thread.sleep(60 * 1000);
                    cleanupEmptyQueues();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "rate-limit-queue-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    private void cleanupEmptyQueues() {
        requestQueues.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        log.debug("队列清理完成，当前队列数: {}", requestQueues.size());
    }
    
    @Override
    protected Object doHandleRejectedRequest(Method method, Object[] args, Object target) throws Throwable {
        String resourceName = target.getClass().getName() + "#" + method.getName();
        
        // 获取或创建请求队列
        BlockingQueue<RequestTask> queue = requestQueues.computeIfAbsent(
                resourceName, k -> new LinkedBlockingQueue<>(defaultQueueCapacity)
        );
        
        // 创建请求任务
        RequestTask task = new RequestTask(method, args, target);
        
        // 尝试将请求加入队列
        if (!queue.offer(task)) {
            // 队列已满，直接拒绝
            log.warn("请求队列已满，拒绝请求: {}, 当前队列长度: {}", resourceName, queue.size());
            throw new RateLimitException("Request queue is full for resource: " + resourceName);
        }
        
        log.debug("请求加入队列等待执行: {}, 当前队列长度: {}", resourceName, queue.size());
        
        // 提交任务到线程池执行
        Future<Object> future = executorService.submit(task);
        
        try {
            // 等待任务执行完成，设置超时时间
            return future.get(defaultTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 取消任务并从队列中移除
            future.cancel(true);
            queue.remove(task);
            log.warn("请求等待超时: {}", resourceName);
            throw new RateLimitException("Request timed out while waiting in queue: " + resourceName);
        } catch (ExecutionException e) {
            // 如果任务执行过程中抛出异常，则传递原始异常
            log.error("请求执行异常: {}", resourceName, e.getCause());
            throw e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("请求等待被中断: {}", resourceName);
            throw new RateLimitException("Request waiting was interrupted: " + resourceName);
        }
    }
    
    /**
     * 请求任务，封装请求的相关信息
     */
    private static class RequestTask implements Callable<Object> {
        private final Method method;
        private final Object[] args;
        private final Object target;
        
        public RequestTask(Method method, Object[] args, Object target) {
            this.method = method;
            this.args = args;
            this.target = target;
        }
        
        @Override
        public Object call() throws Exception {
            try {
                return method.invoke(target, args);
            } catch (Exception e) {
                if (e instanceof InvocationTargetException) {
                    throw new ExecutionException(((InvocationTargetException) e).getTargetException());
                }
                throw e;
            }
        }
    }
    
    /**
     * 关闭处理器，清理资源
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        requestQueues.clear();
        log.info("请求排队降级处理器已关闭");
    }
}