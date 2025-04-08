package com.weihua.trace;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

/**
 * 追踪上下文，存储单个调用链的追踪信息
 */
@Data
@Log4j2
public class TraceContext {
    // 全局追踪ID
    private String traceId;
    // 当前调用的spanId
    private String spanId;
    // 父级调用的spanId，如果是根请求则为null
    private String parentSpanId;
    // 服务名称
    private String serviceName;
    // 方法名称
    private String methodName;
    // 开始时间
    private long startTime;
    // 结束时间
    private long endTime;
    // 是否已上报
    private boolean reported = false;
    // 自定义标签
    private Map<String, String> tags = new HashMap<>();

    public TraceContext() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 创建一个新的根追踪上下文
     */
    public static TraceContext newRootContext(String serviceName, String methodName) {
        TraceContext context = new TraceContext();
        context.traceId = TraceIdGenerator.generateTraceId();
        context.spanId = TraceIdGenerator.generateSpanId();
        context.parentSpanId = null;
        context.serviceName = serviceName;
        context.methodName = methodName;
        return context;
    }

    /**
     * 创建子追踪上下文
     */
    public TraceContext newChildContext(String childServiceName, String childMethodName) {
        TraceContext child = new TraceContext();
        child.traceId = this.traceId;
        child.parentSpanId = this.spanId;
        child.spanId = TraceIdGenerator.generateSpanId();
        child.serviceName = childServiceName;
        child.methodName = childMethodName;
        return child;
    }

    /**
     * 完成当前Span并上报数据
     */
    public void finish() {
        if (endTime == 0) {
            this.endTime = System.currentTimeMillis();
        }

        // 如果已上报，不重复上报
        if (reported) {
            return;
        }

        // 将当前Span上报到Zipkin
        reportToZipkin();

        // 标记为已上报
        reported = true;
    }

    /**
     * 上报数据到Zipkin
     */
    private void reportToZipkin() {
        try {
            // 计算耗时
            long duration = endTime - startTime;

            // 获取成功状态和错误信息
            boolean success = true;
            String errorMessage = null;

            // 从标签中获取状态信息
            if (tags.containsKey("success")) {
                success = Boolean.parseBoolean(tags.get("success"));
            }

            if (tags.containsKey("error")) {
                errorMessage = tags.get("error");
                success = false;
            }

            // 确定请求类型
            String type = tags.getOrDefault("type", "RPC");

            // 构建Span名称
            String name = methodName;
            if (serviceName != null && !serviceName.isEmpty()) {
                name = serviceName + "." + methodName;
            }

            if ("type".equals("RPC")) {
                log.info("胡伟华name:{}", name);
            }

            // 调用ZipkinReporter上报数据
            ZipkinReporter.reportSpan(
                    traceId,
                    spanId,
                    parentSpanId,
                    name,
                    startTime,
                    duration,
                    serviceName,
                    type,
                    success,
                    errorMessage);
        } catch (Exception e) {
            // 上报过程中的异常不应影响业务流程
            log.error("上报追踪数据异常:{}", e.getMessage());
        }
    }

    /**
     * 添加标签
     */
    public TraceContext addTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

}