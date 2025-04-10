/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:42:51
 * @LastEditTime: 2025-04-10 01:42:55
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.config;

import java.util.Map;

/**
 * 配置接口
 */
public interface Configuration {
    /**
     * 获取字符串配置
     */
    String getString(String key);

    /**
     * 获取字符串配置，如果不存在则返回默认值
     */
    String getString(String key, String defaultValue);

    /**
     * 获取整数配置，如果不存在或无法解析则返回默认值
     */
    int getInt(String key, int defaultValue);

    /**
     * 获取布尔配置，如果不存在则返回默认值
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * 获取指定前缀的所有配置
     */
    Map<String, String> getProperties(String prefix);

    /**
     * 设置配置
     */
    void setProperty(String key, String value);

    /**
     * 获取双精度浮点数配置，如果不存在或无法解析则返回默认值
     */
    default double getDouble(String key, double defaultValue) {
        String value = getString(key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 获取长整型配置，如果不存在或无法解析则返回默认值
     */
    default long getLong(String key, long defaultValue) {
        String value = getString(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-10 01:42:51
 * 
 * @LastEditTime: 2025-04-10 01:42:51
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
