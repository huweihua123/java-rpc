/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:42:57
 * @LastEditTime: 2025-04-10 01:43:55
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.config;

/**
 * 配置模式枚举
 */
public enum ConfigMode {
    /**
     * 优先使用属性文件配置（默认）
     */
    PROPERTIES_FIRST,

    /**
     * 优先使用YAML配置
     */
    YAML_FIRST,

    /**
     * 仅使用YAML配置
     */
    YAML_ONLY,

    /**
     * Spring环境配置优先
     */
    SPRING_FIRST
}
/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-10 01:42:57
 * 
 * @LastEditTime: 2025-04-10 01:42:57
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
