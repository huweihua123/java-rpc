/*
 * @Author: weihua hu
 * @Date: 2025-04-03 18:48:40
 * @LastEditTime: 2025-04-03 18:48:41
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.trace;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class RequestTraceContextManager {
    // 使用ConcurrentHashMap存储请求ID与追踪上下文的映射
    private static final Map<String, Map<String, String>> TRACE_CONTEXTS = new ConcurrentHashMap<>();

    // 设置追踪上下文
    public static void setTraceContext(String requestId, Map<String, String> traceContext) {
        TRACE_CONTEXTS.put(requestId, traceContext);
        log.debug("设置请求追踪上下文: requestId={}", requestId);
    }

    // 获取追踪上下文
    public static Map<String, String> getTraceContext(String requestId) {
        return TRACE_CONTEXTS.get(requestId);
    }

    // 清理追踪上下文
    public static void removeTraceContext(String requestId) {
        TRACE_CONTEXTS.remove(requestId);
        log.debug("移除请求追踪上下文: requestId={}", requestId);
    }

    // 定期清理过期的上下文（可选）
    static {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trace-context-cleaner");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(() -> {
            log.debug("开始清理过期的追踪上下文，当前数量: {}", TRACE_CONTEXTS.size());
            // 实际实现可加入时间戳判断逻辑
        }, 60, 60, TimeUnit.SECONDS);
    }
}