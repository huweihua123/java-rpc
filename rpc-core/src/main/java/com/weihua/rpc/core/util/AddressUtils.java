package com.weihua.rpc.core.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 网络地址工具类
 * 提供地址格式转换和验证功能
 */
@Slf4j
public class AddressUtils {

    // IP地址正则表达式模式
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");

    // 主机名正则表达式模式
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$");

    /**
     * 将字符串地址转换为InetSocketAddress对象
     *
     * @param address 格式为"host:port"的地址字符串
     * @return InetSocketAddress对象
     * @throws UnknownHostException     如果主机名无法解析为IP地址
     * @throws IllegalArgumentException 如果地址格式不正确
     */
    public static InetSocketAddress parseAddress(String address) throws UnknownHostException {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("地址不能为空");
        }

        String[] parts = address.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("地址格式不正确: " + address + ", 应为host:port格式");
        }

        String host = parts[0].trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("主机名不能为空: " + address);
        }

        int port;
        try {
            port = Integer.parseInt(parts[1]);
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("端口号超出范围(0-65535): " + port);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("端口号格式不正确: " + parts[1], e);
        }

        // 解析主机名为IP地址
        InetAddress inetAddress = InetAddress.getByName(host);
        return new InetSocketAddress(inetAddress, port);
    }

    /**
     * 检查地址字符串格式是否有效
     *
     * @param address 格式为"host:port"的地址字符串
     * @return 如果格式有效返回true，否则返回false
     */
    public static boolean isValidAddress(String address) {
        try {
            parseAddress(address);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 批量转换地址字符串列表为InetSocketAddress列表
     *
     * @param addressStrings 地址字符串列表
     * @return InetSocketAddress列表
     */
    public static List<InetSocketAddress> parseAddresses(List<String> addressStrings) {
        List<InetSocketAddress> result = new ArrayList<>();
        if (addressStrings == null || addressStrings.isEmpty()) {
            return result;
        }

        for (String addressStr : addressStrings) {
            try {
                result.add(parseAddress(addressStr));
            } catch (Exception e) {
                log.warn("解析地址失败: {}, 原因: {}", addressStr, e.getMessage());
            }
        }
        return result;
    }

    /**
     * 将InetSocketAddress转换为字符串格式
     *
     * @param address InetSocketAddress对象
     * @return 格式为"host:port"的字符串
     */
    public static String toString(InetSocketAddress address) {
        if (address == null) {
            return "";
        }
        return address.getHostString() + ":" + address.getPort();
    }
}