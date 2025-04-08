package com.weihua.trace;

import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.extern.log4j.Log4j2;

/**
 * 追踪上下文载体，负责在不同线程间传递追踪上下文
 */
@Log4j2
public class TraceCarrier {

    // 使用ThreadLocal存储当前线程的追踪上下文
    private static final ThreadLocal<TraceContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 获取当前线程的追踪上下文，如果不存在则创建新的根上下文
     */
    public static TraceContext current() {
        TraceContext context = CONTEXT_HOLDER.get();
        if (context == null) {
            // 创建默认根上下文
            context = TraceContext.newRootContext("unknown", "unknown");
            CONTEXT_HOLDER.set(context);
        }
        return context;
    }

    /**
     * 设置当前线程的追踪上下文
     */
    public static void set(TraceContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 清除当前线程的追踪上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 将追踪上下文信息注入到RPC请求中
     */
    public static void inject(RpcRequest request) {
        try {
            TraceContext context = current();
            request.setTraceId(context.getTraceId());
            request.setSpanId(context.getSpanId());
            request.setParentSpanId(context.getParentSpanId());

            log.debug("注入追踪信息到请求: traceId={}, spanId={}, parentSpanId={}",
                    context.getTraceId(), context.getSpanId(), context.getParentSpanId());
        } catch (Exception e) {
            log.warn("注入追踪信息失败", e);
        }
    }

    /**
     * 从RPC请求中提取追踪上下文信息
     */
    public static TraceContext extract(RpcRequest request) {
        try {
            // 如果没有traceId，说明对方没有传递追踪信息，创建新的根上下文
            if (request.getTraceId() == null || request.getTraceId().isEmpty()) {
                TraceContext context = TraceContext.newRootContext(
                        request.getInterfaceName(),
                        request.getMethodName());
                set(context);
                return context;
            }

            // 使用请求中的追踪信息创建上下文
            TraceContext context = new TraceContext();
            context.setTraceId(request.getTraceId());
            // 请求中的spanId成为当前上下文的parentSpanId
            context.setParentSpanId(request.getSpanId());
            // 生成新的spanId作为服务端处理的标识
            context.setSpanId(TraceIdGenerator.generateSpanId());
            context.setServiceName(request.getInterfaceName());
            context.setMethodName(request.getMethodName());

            // 设置到当前线程
            set(context);

            log.debug("从请求提取追踪信息: traceId={}, spanId={}, parentSpanId={}",
                    context.getTraceId(), context.getSpanId(), context.getParentSpanId());

            return context;
        } catch (Exception e) {
            log.warn("提取追踪信息失败", e);
            return current(); // 使用或创建默认上下文
        }
    }

    /**
     * 从RPC响应中提取追踪信息并更新当前上下文
     */
    public static void extract(RpcResponse response) {
        try {
            if (response != null) {
                TraceContext context = current();

                // 记录服务端返回的span信息，用于分析
                if (response.getServerSpanId() != null) {
                    context.addTag("server.span_id", response.getServerSpanId());
                }

                // 记录调用是否成功
                context.addTag("success", String.valueOf(response.getCode() == RpcResponse.SUCCESS_CODE));

                // 如果有错误，记录错误信息
                if (response.hasError() && response.getError() != null) {
                    context.addTag("error", response.getError());
                }

                // 计算耗时
                long duration = System.currentTimeMillis() - context.getStartTime();
                context.addTag("duration_ms", String.valueOf(duration));

                // 标记当前span完成
                context.finish();

                log.debug("提取响应追踪信息完成: traceId={}, spanId={}, duration={}ms",
                        context.getTraceId(), context.getSpanId(), duration);
            }
        } catch (Exception e) {
            log.warn("提取响应追踪信息失败", e);
        }
    }

    /**
     * 将追踪上下文信息注入到RPC响应中
     */
    public static void inject(RpcResponse response) {
        try {
            TraceContext context = current();
            response.setTraceId(context.getTraceId());
            response.setSpanId(context.getParentSpanId()); // 回传原始请求的spanId
            response.setServerSpanId(context.getSpanId()); // 服务端处理的spanId

            log.debug("注入追踪信息到响应: traceId={}, spanId={}, serverSpanId={}",
                    context.getTraceId(), response.getSpanId(), response.getServerSpanId());
        } catch (Exception e) {
            log.warn("注入追踪信息到响应失败", e);
        }
    }
}