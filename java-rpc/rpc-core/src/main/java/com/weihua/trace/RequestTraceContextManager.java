/*
 * @Author: weihua hu
 * @Date: 2025-04-03 18:48:40
 * @LastEditTime: 2025-04-04 20:42:52
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.trace;

import lombok.extern.log4j.Log4j2;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class RequestTraceContextManager {
    // 使用ConcurrentHashMap存储请求ID与追踪上下文的映射
    private static final Map<String, Map<String, String>> TRACE_CONTEXTS = new ConcurrentHashMap<>();

    // 心跳请求的空上下文 - 避免频繁创建空对象
    private static final Map<String, String> EMPTY_CONTEXT = Collections.emptyMap();

    // 设置追踪上下文
    public static void setTraceContext(String requestId, Map<String, String> traceContext) {
        if (requestId == null) {
            log.warn("尝试设置追踪上下文时收到null请求ID");
            return;
        }

        // 心跳请求不需要跟踪上下文
        if (requestId.startsWith("heartbeat-")) {
            return;
        }

        TRACE_CONTEXTS.put(requestId, traceContext);
        log.debug("设置请求追踪上下文: requestId={}", requestId);
    }

    // 获取追踪上下文
    public static Map<String, String> getTraceContext(String requestId) {
        if (requestId == null) {
            log.warn("尝试获取追踪上下文时收到null请求ID");
            return EMPTY_CONTEXT;
        }

        // 心跳请求使用空上下文
        if (requestId.startsWith("heartbeat-")) {
            return EMPTY_CONTEXT;
        }

        Map<String, String> context = TRACE_CONTEXTS.get(requestId);
        return context != null ? context : EMPTY_CONTEXT;
    }

    // 清理追踪上下文
    public static void removeTraceContext(String requestId) {
        if (requestId == null || requestId.startsWith("heartbeat-")) {
            return;
        }

        TRACE_CONTEXTS.remove(requestId);
        log.debug("移除请求追踪上下文: requestId={}", requestId);
    }

    // 定期清理过期的上下文
    static {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trace-context-cleaner");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(() -> {
            int before = TRACE_CONTEXTS.size();
            // 清理过期的追踪上下文（可根据实际需要实现）
            // 这里仅打印当前数量
            log.debug("追踪上下文清理任务执行, 当前追踪上下文数量: {}", before);
        }, 60, 60, TimeUnit.SECONDS);
    }
}