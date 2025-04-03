/*
 * @Author: weihua hu
 * @Date: 2025-04-01 13:38:57
 * @LastEditTime: 2025-04-03 15:05:30
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.trace.interceptor;

import com.weihua.trace.TraceIdGenerator;
import com.weihua.trace.ZipkinReporter;
import common.trace.TraceContext;

public class ServerTraceInterceptor {
    public static void beforeHandle() {
        String traceId = TraceContext.getTraceId();
        String parentSpanId = TraceContext.getParentSpanId();
        String spanId = TraceIdGenerator.generateSpanId();
        TraceContext.setTraceId(traceId);
        TraceContext.setSpanId(spanId);
        TraceContext.setParentSpanId(parentSpanId);

        // 记录服务端 Span
        long startTimestamp = System.currentTimeMillis();
        TraceContext.setStartTimestamp(String.valueOf(startTimestamp));
    }

    public static void afterHandle(String serviceName, boolean success, String errorMessage) {
        long endTimestamp = System.currentTimeMillis();
        long startTimestamp = Long.valueOf(TraceContext.getStartTimestamp());
        long duration = endTimestamp - startTimestamp;

        ZipkinReporter.reportSpan(
                TraceContext.getTraceId(),
                TraceContext.getSpanId(),
                TraceContext.getParentSpanId(),
                "server-" + serviceName,
                startTimestamp,
                duration,
                serviceName,
                "server",
                success, // 添加成功状态
                errorMessage // 添加错误信息
        );

        TraceContext.clear();
    }
}
