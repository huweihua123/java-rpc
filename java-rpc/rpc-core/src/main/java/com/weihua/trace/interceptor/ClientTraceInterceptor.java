/*
 * @Author: weihua hu
 * @Date: 2025-04-01 13:26:09
 * @LastEditTime: 2025-04-03 15:05:16
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.trace.interceptor;

import com.weihua.trace.TraceIdGenerator;
import com.weihua.trace.ZipkinReporter;
import common.trace.TraceContext;

public class ClientTraceInterceptor {
    public static void beforeInvoke() {
        String traceId = TraceContext.getTraceId();

        if (traceId == null) {
            traceId = TraceIdGenerator.generateTraceId();
            TraceContext.setTraceId(traceId);
        }
        String spanId = TraceIdGenerator.generateSpanId();
        TraceContext.setSpanId(spanId);

        long startTimestamp = System.currentTimeMillis();
        TraceContext.setStartTimestamp(String.valueOf(startTimestamp));
    }

    public static void afterInvoke(String serviceName, boolean success, String errorMessage) {
        long endTimeStamp = System.currentTimeMillis();
        long startTimeStamp = Long.valueOf(TraceContext.getStartTimestamp());

        long duration = endTimeStamp - startTimeStamp;

        ZipkinReporter.reportSpan(
                TraceContext.getTraceId(),
                TraceContext.getSpanId(),
                TraceContext.getParentSpanId(),
                "client-" + serviceName,
                startTimeStamp,
                duration,
                serviceName,
                "client",
                success, // 添加成功状态
                errorMessage // 添加错误信息
        );

        TraceContext.clear();
    }

}
