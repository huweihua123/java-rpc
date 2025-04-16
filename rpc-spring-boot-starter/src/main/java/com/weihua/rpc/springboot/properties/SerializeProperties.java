/*
 * @Author: weihua hu
 * @Date: 2025-04-13 19:47:14
 * @LastEditTime: 2025-04-15 02:12:09
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.springboot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 序列化配置属性
 */
@ConfigurationProperties(prefix = "rpc.serialize")
@Data
public class SerializeProperties {

    /**
     * 序列化类型: json, protobuf, kryo, hessian, etc.
     */
    private String type = "json";

    /**
     * 是否启用压缩
     */
    private boolean compression = false;

    /**
     * 压缩阈值(字节)，超过该阈值才会压缩
     */
    private int compressionThreshold = 2048;
}