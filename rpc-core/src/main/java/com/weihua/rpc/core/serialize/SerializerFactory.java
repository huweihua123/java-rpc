/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:08:33
 * @LastEditTime: 2025-04-10 02:08:35
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.serialize;

import com.weihua.rpc.core.serialize.impl.JsonSerializer;
import com.weihua.rpc.core.serialize.impl.ProtobufSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器工厂，用于获取不同类型的序列化器
 */
public class SerializerFactory {

    private static final Map<String, Serializer> SERIALIZERS = new ConcurrentHashMap<>();
    private static final Map<Byte, Serializer> SERIALIZERS_BY_TYPE = new ConcurrentHashMap<>();

    // 默认序列化器
    private static final Serializer DEFAULT_SERIALIZER = new JsonSerializer();

    static {
        // 注册序列化器
        registerSerializer(new JsonSerializer());
        registerSerializer(new ProtobufSerializer());
    }

    /**
     * 根据名称获取序列化器
     * 
     * @param name 序列化器名称
     * @return 序列化器实例
     */
    public static Serializer getSerializer(String name) {
        return SERIALIZERS.getOrDefault(name.toLowerCase(), DEFAULT_SERIALIZER);
    }

    /**
     * 根据类型获取序列化器
     * 
     * @param type 序列化器类型编号
     * @return 序列化器实例
     */
    public static Serializer getSerializer(byte type) {
        return SERIALIZERS_BY_TYPE.getOrDefault(type, DEFAULT_SERIALIZER);
    }

    /**
     * 注册序列化器
     * 
     * @param serializer 序列化器实例
     */
    public static void registerSerializer(Serializer serializer) {
        SERIALIZERS.put(serializer.getName().toLowerCase(), serializer);
        SERIALIZERS_BY_TYPE.put(serializer.getType(), serializer);
    }

    /**
     * 获取默认序列化器
     * 
     * @return 默认序列化器实例
     */
    public static Serializer getDefaultSerializer() {
        return DEFAULT_SERIALIZER;
    }
}
