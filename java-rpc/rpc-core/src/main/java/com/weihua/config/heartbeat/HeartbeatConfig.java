package com.weihua.config.heartbeat;

import common.config.ConfigurationManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

/**
 * 心跳配置管理类
 * 集中管理心跳相关配置参数
 */
@Log4j2
public class HeartbeatConfig {
    private static final HeartbeatConfig INSTANCE = new HeartbeatConfig();

    // 心跳超时配置
    @Getter
    private int readerIdleTime; // 读空闲超时时间
    @Getter
    private int writerIdleTime; // 写空闲超时时间
    @Getter
    private int allIdleTime; // 读写空闲超时时间
    @Getter
    private TimeUnit timeUnit; // 时间单位

    // 心跳发送间隔
    @Getter
    private int heartbeatInterval; // 客户端发送心跳间隔
    @Getter
    private int heartbeatResponseTimeout; // 心跳响应超时时间

    // 容错配置
    @Getter
    private int maxHeartbeatFailures; // 最大连续心跳失败次数

    // 重连配置
    @Getter
    private boolean autoReconnect; // 是否自动重连
    @Getter
    private int maxReconnectAttempts; // 最大重连尝试次数
    @Getter
    private int initialReconnectDelay; // 初始重连延迟(毫秒)
    @Getter
    private int maxReconnectDelay; // 最大重连延迟(毫秒)
    @Getter
    private float reconnectBackoffMultiplier; // 重连延迟增长因子

    private HeartbeatConfig() {
        loadConfig();
    }

    public static HeartbeatConfig getInstance() {
        return INSTANCE;
    }

    /**
     * 加载心跳配置
     */
    public void loadConfig() {
        ConfigurationManager config = ConfigurationManager.getInstance();

        // 加载超时配置
        this.readerIdleTime = config.getInt("rpc.heartbeat.reader.idle.time", 60);
        this.writerIdleTime = config.getInt("rpc.heartbeat.writer.idle.time", 30);
        this.allIdleTime = config.getInt("rpc.heartbeat.all.idle.time", 0);

        // 加载时间单位
        String timeUnitName = config.getString("rpc.heartbeat.time.unit", "SECONDS");
        try {
            this.timeUnit = TimeUnit.valueOf(timeUnitName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的时间单位: {}, 使用默认值SECONDS", timeUnitName);
            this.timeUnit = TimeUnit.SECONDS;
        }

        // 加载发送间隔配置
        this.heartbeatInterval = config.getInt("rpc.heartbeat.interval", 30);
        this.heartbeatResponseTimeout = config.getInt("rpc.heartbeat.response.timeout", 5);

        // 加载容错配置
        this.maxHeartbeatFailures = config.getInt("rpc.heartbeat.max.failures", 3);

        // 加载重连配置
        this.autoReconnect = config.getBoolean("rpc.heartbeat.auto.reconnect", true);
        this.maxReconnectAttempts = config.getInt("rpc.heartbeat.max.reconnect.attempts", 10);
        this.initialReconnectDelay = config.getInt("rpc.heartbeat.initial.reconnect.delay", 1000);
        this.maxReconnectDelay = config.getInt("rpc.heartbeat.max.reconnect.delay", 30000);
        this.reconnectBackoffMultiplier = config.getFloat("rpc.heartbeat.reconnect.backoff.multiplier", 1.5f);

        log.info("心跳配置加载完成: 读空闲={}{}，写空闲={}{}，心跳间隔={}{}，最大失败次数={}，自动重连={}",
                readerIdleTime, timeUnit, writerIdleTime, timeUnit,
                heartbeatInterval, timeUnit, maxHeartbeatFailures, autoReconnect);
    }

    /**
     * 刷新配置
     */
    public void refreshConfig() {
        loadConfig();
        log.info("心跳配置已刷新");
    }
}
