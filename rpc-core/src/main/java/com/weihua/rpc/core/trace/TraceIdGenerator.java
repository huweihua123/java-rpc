/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:28:36
 * @LastEditTime: 2025-04-10 02:28:38
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.trace;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 追踪ID生成器
 */
public class TraceIdGenerator {

    // 本机IP地址的最后一段，用于标识不同机器
    private static final String IP_SEGMENT;

    // 进程ID
    private static final String PROCESS_ID;

    // spanId计数器，用于同一个trace内的不同span
    private static final AtomicInteger SPAN_COUNTER = new AtomicInteger(0);

    static {
        // 尝试获取本机IP地址最后一段
        String ipSegment;
        try {
            String hostAddress = java.net.InetAddress.getLocalHost().getHostAddress();
            ipSegment = hostAddress.substring(hostAddress.lastIndexOf('.') + 1);
        } catch (Exception e) {
            // 获取失败则使用随机数
            ipSegment = String.valueOf(ThreadLocalRandom.current().nextInt(1, 256));
        }
        IP_SEGMENT = ipSegment;

        // 获取进程ID
        String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        PROCESS_ID = processName.split("@")[0];
    }

    /**
     * 生成追踪ID
     * 
     * @return 追踪ID
     */
    public static String generateTraceId() {
        // 格式: 时间戳-机器标识-进程ID-随机数
        return System.currentTimeMillis() + "-" +
                IP_SEGMENT + "-" +
                PROCESS_ID + "-" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成Span ID
     * 
     * @return Span ID
     */
    public static String generateSpanId() {
        // 格式: 计数器-随机数
        return SPAN_COUNTER.incrementAndGet() + "-" +
                ThreadLocalRandom.current().nextInt(1000000);
    }
}
