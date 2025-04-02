package com.weihua.trace;

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

    public static void afterInvoke(String serviceName) {
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
                "client"
        );

        TraceContext.clear();
    }

}
