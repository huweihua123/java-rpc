package com.weihua.client.trace;

import java.util.concurrent.ConcurrentHashMap;

import com.weihua.trace.TraceContext;
import com.weihua.trace.TraceIdGenerator;

import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.extern.log4j.Log4j2;

/**
 * 客户端追踪处理器，用于管理请求的追踪上下文
 */
@Log4j2
public class ClientTraceHandler {

    // 使用ConcurrentHashMap存储requestId到追踪上下文的映射
    private static final ConcurrentHashMap<String, TraceContext> REQUEST_CONTEXT_MAP = new ConcurrentHashMap<>();

    /**
     * 处理发送的RPC请求，创建追踪上下文并绑定到请求ID
     * 
     * @param request     即将发送的RPC请求
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @return 创建的追踪上下文
     */
    public static TraceContext handleRequest(RpcRequest request, String serviceName, String methodName) {
        TraceContext context = TraceContext.newRootContext(serviceName, methodName);

        // 添加客户端特有标签
        context.addTag("client.timestamp", String.valueOf(System.currentTimeMillis()));
        context.addTag("client.thread", Thread.currentThread().getName());
        context.addTag("type", "CLIENT");

        // 注入追踪信息到请求
        request.setTraceId(context.getTraceId());
        request.setSpanId(context.getSpanId());
        request.setParentSpanId(context.getParentSpanId());

        // 存储到映射表中
        REQUEST_CONTEXT_MAP.put(request.getRequestId(), context);

        log.debug("客户端发送请求: traceId={}, spanId={}, parentSpanId={}, requestId={}",
                context.getTraceId(), context.getSpanId(), context.getParentSpanId(), request.getRequestId());

        return context;
    }

    /**
     * 处理接收到的RPC响应，提取追踪信息并完成上下文
     * 
     * @param response     接收到的RPC响应
     * @param success      调用是否成功
     * @param errorMessage 错误信息（如果有）
     */
    public static void handleResponse(RpcResponse response, boolean success, String errorMessage) {
        try {
            String requestId = response.getRequestId();
            TraceContext context = requestId != null ? REQUEST_CONTEXT_MAP.remove(requestId) : null;

            if (context == null) {
                log.warn("未找到请求ID对应的追踪上下文: {}", requestId);
                return;
            }

            // 记录服务端返回的span信息
            if (response.getServerSpanId() != null) {
                context.addTag("server.span_id", response.getServerSpanId());
            }

            // 记录调用结果
            context.addTag("success", String.valueOf(success));

            // 如果有错误，记录错误信息
            if (!success && errorMessage != null) {
                context.addTag("error", errorMessage);
            }

            // 计算耗时
            long duration = System.currentTimeMillis() - context.getStartTime();
            context.addTag("duration_ms", String.valueOf(duration));

            // 完成span并上报
            context.finish();

            log.debug("客户端收到响应: traceId={}, spanId={}, 处理耗时={}ms, requestId={}",
                    context.getTraceId(), context.getSpanId(), duration, requestId);

        } catch (Exception e) {
            log.warn("处理响应追踪信息失败", e);
        }
    }

    /**
     * 获取指定请求ID关联的追踪上下文
     */
    public static TraceContext getContext(String requestId) {
        return REQUEST_CONTEXT_MAP.get(requestId);
    }

    /**
     * 清理超时的追踪上下文，避免内存泄漏
     */
    public static void cleanupStaleContexts(long timeoutMs) {
        long currentTime = System.currentTimeMillis();
        REQUEST_CONTEXT_MAP.forEach((requestId, context) -> {
            if (currentTime - context.getStartTime() > timeoutMs) {
                log.warn("清理超时的客户端追踪上下文: requestId={}, traceId={}, 已过时 {}ms",
                        requestId, context.getTraceId(), currentTime - context.getStartTime());
                REQUEST_CONTEXT_MAP.remove(requestId);
            }
        });
    }

    /**
     * 获取当前存储的上下文数量，用于监控
     */
    public static int getContextCount() {
        return REQUEST_CONTEXT_MAP.size();
    }
}