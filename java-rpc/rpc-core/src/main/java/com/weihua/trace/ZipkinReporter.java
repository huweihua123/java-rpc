/*
 * @Author: weihua hu
 * @Date: 2025-03-31 19:15:33
 * @LastEditTime: 2025-04-06 23:56:39
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.trace;

import lombok.extern.log4j.Log4j2;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

@Log4j2
public class ZipkinReporter {
    private static AsyncReporter<Span> reporter;
    private static OkHttpSender sender;

    // 初始化标志，用于延迟初始化
    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    /**
     * 初始化 Zipkin Reporter
     * 使用懒加载模式，仅在首次使用时初始化
     */
    private static void initializeReporter() {
        if (!initialized) {
            synchronized (INIT_LOCK) {
                if (!initialized) {
                    if (!ZipkinConfig.isEnabled()) {
                        log.info("Zipkin 追踪已禁用，不会报告追踪数据");
                        initialized = true;
                        return;
                    }

                    try {
                        String zipkinUrl = ZipkinConfig.getZipkinUrl();
                        int batchSize = ZipkinConfig.getSpanBatchSize();
                        int flushInterval = ZipkinConfig.getFlushIntervalSeconds();

                        // 创建 Zipkin 发送器
                        sender = OkHttpSender.create(zipkinUrl);

                        // 创建异步 Reporter
                        reporter = AsyncReporter.builder(sender)
                                .messageMaxBytes(5 * 1024 * 1024) // 5MB
                                .messageTimeout(flushInterval, java.util.concurrent.TimeUnit.SECONDS)
                                .queuedMaxSpans(batchSize)
                                .build();

                        log.info("Zipkin Reporter 初始化成功 - URL: {}", zipkinUrl);
                    } catch (Exception e) {
                        log.error("初始化 Zipkin Reporter 失败", e);
                    } finally {
                        initialized = true;
                    }
                }
            }
        }
    }

    /**
     * 上报 Span 数据到 Zipkin
     */
    public static void reportSpan(
            String traceId, String spanId, String parentSpanId,
            String name, long startTimestamp, long duration,
            String serviceName, String type,
            boolean success, String errorMessage) {

        // 如果 Zipkin 未开启，直接返回
        if (!ZipkinConfig.isEnabled()) {
            return;
        }

        // 懒加载初始化
        initializeReporter();

        // 如果初始化失败，reporter 可能为空
        if (reporter == null) {
            log.warn("无法上报 span: Zipkin Reporter 未正确初始化");
            return;
        }

        Span.Builder spanBuilder = Span.newBuilder()
                .traceId(traceId)
                .id(spanId)
                .parentId(parentSpanId)
                .name(name)
                .timestamp(startTimestamp * 1000) // Zipkin 使用微秒
                .duration(duration * 1000) // Zipkin 使用微秒
                .putTag("service", serviceName)
                .putTag("type", type);

        // 添加状态标签
        spanBuilder.putTag("success", String.valueOf(success));

        // 如果失败，添加错误信息
        if (!success && errorMessage != null) {
            spanBuilder.putTag("error", errorMessage);
        }

        Span span = spanBuilder.build();
        reporter.report(span);

        if (log.isDebugEnabled()) {
            log.debug("span:{} 上报成功 [状态:{}]", span, success ? "成功" : "失败");
        }
    }

    public static void close() {
        if (reporter != null) {
            try {
                reporter.close();
                log.info("Zipkin Reporter 已关闭");
            } catch (Exception e) {
                log.error("关闭 Zipkin Reporter 时发生异常", e);
            }
        }

        if (sender != null) {
            try {
                sender.close();
            } catch (Exception e) {
                log.error("关闭 Zipkin Sender 时发生异常", e);
            }
        }
    }

    /**
     * 重新初始化 Reporter
     * 用于配置变更后刷新
     */
    public static void reset() {
        close();
        initialized = false;
        log.info("Zipkin Reporter 已重置，将在下次使用时重新初始化");
    }
}