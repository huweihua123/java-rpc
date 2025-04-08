/*
 * @Author: weihua hu
 * @Date: 2025-04-07 17:36:27
 * @LastEditTime: 2025-04-08 16:04:32
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.server.handler;

import java.util.concurrent.ConcurrentHashMap;

import com.weihua.trace.TraceContext;
import com.weihua.trace.TraceIdGenerator;

import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.extern.log4j.Log4j2;

/**
 * 服务端追踪处理器，用于处理请求和响应的追踪信息
 */
@Log4j2
public class TraceServerHandler {

    // 使用ConcurrentHashMap存储requestId到追踪上下文的映射
    private static final ConcurrentHashMap<String, TraceContext> REQUEST_CONTEXT_MAP = new ConcurrentHashMap<>();

    /**
     * 处理收到的RPC请求，提取追踪信息
     * 
     * @param request 收到的RPC请求
     * @return 创建的追踪上下文
     */
    public static TraceContext handleRequest(RpcRequest request) {
        TraceContext context;

        // 从请求中提取追踪信息
        if (request.getTraceId() == null || request.getTraceId().isEmpty()) {
            // 创建新的根上下文
            context = TraceContext.newRootContext(
                    request.getInterfaceName(),
                    request.getMethodName());
        } else {
            // 使用请求中的追踪信息创建上下文
            context = new TraceContext();
            context.setTraceId(request.getTraceId());
            context.setParentSpanId(request.getSpanId());
            context.setSpanId(TraceIdGenerator.generateSpanId());
            context.setServiceName(request.getInterfaceName());
            context.setMethodName(request.getMethodName());
        }

        // 添加服务端特有标签
        context.addTag("server.timestamp", String.valueOf(System.currentTimeMillis()));
        context.addTag("server.thread", Thread.currentThread().getName());
        context.addTag("type", "SERVER");

        // 存储到映射表中
        REQUEST_CONTEXT_MAP.put(request.getRequestId(), context);

        log.debug("服务端接收请求: traceId={}, spanId={}, parentSpanId={}, requestId={}",
                context.getTraceId(), context.getSpanId(), context.getParentSpanId(), request.getRequestId());

        return context;
    }

    /**
     * 处理即将返回的RPC响应，注入追踪信息
     * 
     * @param response 即将返回的RPC响应
     */

    public static void handleResponse(RpcResponse response) {
        try {
            // 获取与请求ID绑定的上下文
            String requestId = response.getRequestId();
            TraceContext context = requestId != null ? REQUEST_CONTEXT_MAP.get(requestId) : null;

            if (context == null) {
                log.warn("未找到请求ID对应的追踪上下文: {}", requestId);
                return;
            }

            // 记录服务端处理的耗时和其他追踪信息
            long duration = System.currentTimeMillis() - context.getStartTime();
            context.addTag("server.duration_ms", String.valueOf(duration));
            boolean success = response.getCode() == RpcResponse.SUCCESS_CODE;
            context.addTag("success", String.valueOf(success));

            if (!success && response.getError() != null) {
                context.addTag("error", response.getError());
            }

            // 注入追踪信息到响应
            response.setTraceId(context.getTraceId());
            response.setSpanId(context.getParentSpanId());
            response.setServerSpanId(context.getSpanId());

            // 完成span并上报
            context.finish();

            log.debug("服务端返回响应: traceId={}, spanId={}, 处理耗时={}ms, requestId={}",
                    context.getTraceId(), context.getSpanId(), duration, requestId);

            // 清除请求ID对应的上下文
            REQUEST_CONTEXT_MAP.remove(requestId);

        } catch (Exception e) {
            log.warn("处理响应追踪信息失败", e);
        }
    }

    /**
     * 清理超时的追踪上下文，避免内存泄漏
     * 
     * @param timeoutMs 超时时间（毫秒）
     */
    public static void cleanupStaleContexts(long timeoutMs) {
        long currentTime = System.currentTimeMillis();
        REQUEST_CONTEXT_MAP.forEach((requestId, context) -> {
            if (currentTime - context.getStartTime() > timeoutMs) {
                log.warn("清理超时的追踪上下文: requestId={}, traceId={}, 已过时 {}ms",
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