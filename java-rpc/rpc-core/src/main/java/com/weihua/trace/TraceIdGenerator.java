/*
 * @Author: weihua hu
 * @Date: 2025-03-31 19:18:37
 * @LastEditTime: 2025-04-07 01:20:32
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.trace;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 高效的追踪ID和SpanID生成器
 */
public class TraceIdGenerator {

    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final String IP_16 = getIp16();
    private static final String PID = getPid();

    /**
     * 生成TraceId: ip + timestamp + pid + seq
     */
    public static String generateTraceId() {
        // 使用IP末尾部分+时间戳前缀+进程ID+序列号，更加高效，且便于排查
        return IP_16 +
                Long.toHexString(System.currentTimeMillis()) +
                PID +
                Long.toHexString(SEQUENCE.incrementAndGet() & 0xffff);
    }

    /**
     * 生成SpanId
     */
    public static String generateSpanId() {
        // 使用更简短的spanId
        return Long.toHexString(System.nanoTime());
    }

    private static String getIp16() {
        // 这里简化处理，实际应获取真实IP并转16进制
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            byte[] address = localHost.getAddress();
            if (address.length > 2) {
                return String.format("%02x%02x", address[address.length - 2], address[address.length - 1]);
            }
        } catch (Exception ignored) {
        }
        return "0000";
    }

    private static String getPid() {
        // 获取进程ID
        String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        try {
            return name.contains("@")
                    ? Integer.toHexString(Integer.parseInt(name.substring(0, name.indexOf('@'))) & 0xffff)
                    : "0000";
        } catch (Exception e) {
            return "0000";
        }
    }
}
