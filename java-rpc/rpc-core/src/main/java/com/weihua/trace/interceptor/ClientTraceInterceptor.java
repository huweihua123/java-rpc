package com.weihua.trace.interceptor;

import com.weihua.trace.TraceCarrier;
import com.weihua.trace.TraceContext;
import lombok.extern.log4j.Log4j2;

/**
 * 客户端追踪拦截器，用于处理RPC调用的追踪
 */
@Log4j2
public class ClientTraceInterceptor {

    /**
     * 调用前的追踪处理
     */
    public static TraceContext beforeInvoke(String serviceName, String methodName) {
        // 关键修改：清除之前的上下文，确保每次调用都有新的上下文
        TraceCarrier.clear();

        // 创建或获取当前线程的追踪上下文
        TraceContext context = TraceCarrier.current();

        // 如果是新创建的上下文，设置服务和方法名
        if (context.getServiceName() == null || "unknown".equals(context.getServiceName())) {
            context.setServiceName(serviceName);
            context.setMethodName(methodName);
        } else {
            // 如果已有上下文，创建子上下文处理当前调用
            context = context.newChildContext(serviceName, methodName);
            TraceCarrier.set(context);
        }

        // 设置类型为客户端调用
        context.addTag("type", "CLIENT");
        context.addTag("client.timestamp", String.valueOf(System.currentTimeMillis()));
        context.addTag("client.thread", Thread.currentThread().getName());

        log.debug("客户端发起调用: traceId={}, spanId={}, parentSpanId={}",
                context.getTraceId(), context.getSpanId(), context.getParentSpanId());

        return context;
    }

    /**
     * 调用后的追踪处理
     */
    public static void afterInvoke(String serviceName, String methodName, boolean success, String errorMessage) {
        try {
            TraceContext context = TraceCarrier.current();

            // 记录调用结果
            context.addTag("success", String.valueOf(success));

            // 如果失败，记录错误信息
            if (!success && errorMessage != null) {
                context.addTag("error", errorMessage);
            }

            // 记录调用耗时
            long duration = System.currentTimeMillis() - context.getStartTime();
            context.addTag("duration_ms", String.valueOf(duration));

            log.debug("客户端调用完成: traceId={}, spanId={}, success={}, duration={}ms",
                    context.getTraceId(), context.getSpanId(), success, duration);

            // 完成当前span，触发上报
            context.finish();

            // 可选：清除上下文，避免内存泄漏
            TraceCarrier.clear();
        } catch (Exception e) {
            log.warn("处理客户端调用追踪失败", e);
        }
    }
}