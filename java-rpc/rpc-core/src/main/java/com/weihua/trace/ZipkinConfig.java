/*
 * @Author: weihua hu
 * @Date: 2025-04-06 23:55:48
 * @LastEditTime: 2025-04-06 23:55:50
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.trace;

import common.config.ConfigurationManager;
import lombok.extern.log4j.Log4j2;

/**
 * Zipkin 配置管理类
 * 负责从 ConfigurationManager 获取 Zipkin 相关配置
 */
@Log4j2
public class ZipkinConfig {
    // 默认配置值
    private static final String DEFAULT_ZIPKIN_URL = "http://localhost:9411/api/v2/spans";
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_SPAN_BATCH_SIZE = 100;
    private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 1;

    // 配置键
    public static final String CONFIG_ZIPKIN_URL = "rpc.trace.zipkin.url";
    public static final String CONFIG_ZIPKIN_ENABLED = "rpc.trace.zipkin.enabled";
    public static final String CONFIG_ZIPKIN_BATCH_SIZE = "rpc.trace.zipkin.batch.size";
    public static final String CONFIG_ZIPKIN_FLUSH_INTERVAL = "rpc.trace.zipkin.flush.interval";

    private static final ConfigurationManager config = ConfigurationManager.getInstance();

    /**
     * 获取 Zipkin 服务器地址
     */
    public static String getZipkinUrl() {
        return config.getString(CONFIG_ZIPKIN_URL, DEFAULT_ZIPKIN_URL);
    }

    /**
     * 判断 Zipkin 追踪是否启用
     */
    public static boolean isEnabled() {
        return config.getBoolean(CONFIG_ZIPKIN_ENABLED, DEFAULT_ENABLED);
    }

    /**
     * 获取 Span 批处理大小
     */
    public static int getSpanBatchSize() {
        return config.getInt(CONFIG_ZIPKIN_BATCH_SIZE, DEFAULT_SPAN_BATCH_SIZE);
    }

    /**
     * 获取刷新间隔（秒）
     */
    public static int getFlushIntervalSeconds() {
        return config.getInt(CONFIG_ZIPKIN_FLUSH_INTERVAL, DEFAULT_FLUSH_INTERVAL_SECONDS);
    }

    /**
     * 初始化配置
     * 可在应用启动时调用，进行配置验证
     */
    public static void initialize() {
        log.info("Zipkin 配置初始化 - URL: {}, 启用状态: {}, 批处理大小: {}, 刷新间隔: {}秒",
                getZipkinUrl(), isEnabled(), getSpanBatchSize(), getFlushIntervalSeconds());
    }
}