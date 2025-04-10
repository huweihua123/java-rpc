package com.weihua.rpc.core.trace;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 追踪上下文
 * 存储当前调用链的追踪信息
 */
@Data
@Slf4j
public class TraceContext {

    // 追踪ID，唯一标识一次调用链
    private String traceId;

    // 当前Span ID
    private String spanId;

    // 父Span ID
    private String parentSpanId;

    // 服务名称
    private String serviceName;

    // 方法名称
    private String methodName;

    // 开始时间戳
    private long startTime;

    // 结束时间戳
    private long endTime;

    // 标签，用于存储额外信息
    private Map<String, String> tags = new HashMap<>();

    // 是否已完成
    private boolean finished = false;

    // 静态追踪上下文
    private static final ThreadLocal<TraceContext> CONTEXT_HOLDER = new ThreadLocal<>();

    // 采样率计数器
    private static final AtomicInteger SAMPLE_COUNTER = new AtomicInteger(0);

    // 采样率（1/N）
    private static final int SAMPLE_RATE = 10;

    public TraceContext() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 创建新的根上下文
     * 
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @return 新创建的追踪上下文
     */
    public static TraceContext newRootContext(String serviceName, String methodName) {
        TraceContext context = new TraceContext();
        context.traceId = TraceIdGenerator.generateTraceId();
        context.spanId = TraceIdGenerator.generateSpanId();
        context.parentSpanId = "0";
        context.serviceName = serviceName;
        context.methodName = methodName;

        // 设置当前线程的追踪上下文
        CONTEXT_HOLDER.set(context);

        return context;
    }

    /**
     * 获取当前线程的追踪上下文
     * 
     * @return 当前追踪上下文，如果不存在返回null
     */
    public static TraceContext current() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 添加标签
     * 
     * @param key   标签键
     * @param value 标签值
     * @return 当前上下文
     */
    public TraceContext addTag(String key, String value) {
        tags.put(key, value);
        return this;
    }

    /**
     * 完成当前Span
     */
    public void finish() {
        if (!finished) {
            endTime = System.currentTimeMillis();
            finished = true;

            // 进行采样判断
            if (shouldSample()) {
                // 上报追踪信息
                report();
            }

            // 清除当前线程的上下文
            if (CONTEXT_HOLDER.get() == this) {
                CONTEXT_HOLDER.remove();
            }
        }
    }

    /**
     * 是否应该采样
     * 
     * @return 如果应该采样返回true，否则返回false
     */
    private boolean shouldSample() {
        return SAMPLE_COUNTER.incrementAndGet() % SAMPLE_RATE == 0;
    }

    /**
     * 上报追踪信息
     */
    private void report() {
        try {
            // TODO: 将追踪信息上报到追踪系统
            // 这里可以使用追踪系统的API进行上报
            // 例如: zipkin, jaeger, skywalking等

            if (log.isDebugEnabled()) {
                log.debug("上报追踪信息: traceId={}, spanId={}, parentSpanId={}, 服务={}, 方法={}",
                        traceId, spanId, parentSpanId, serviceName, methodName);
            }
        } catch (Exception e) {
            log.warn("上报追踪信息失败", e);
        }
    }

    /**
     * 创建子Span
     * 
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @return 新创建的子Span上下文
     */
    public TraceContext createChildSpan(String serviceName, String methodName) {
        TraceContext childContext = new TraceContext();
        childContext.traceId = this.traceId;
        childContext.parentSpanId = this.spanId;
        childContext.spanId = TraceIdGenerator.generateSpanId();
        childContext.serviceName = serviceName;
        childContext.methodName = methodName;

        // 设置当前线程的追踪上下文
        CONTEXT_HOLDER.set(childContext);

        return childContext;
    }
}
