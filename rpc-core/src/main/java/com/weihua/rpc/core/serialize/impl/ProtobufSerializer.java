/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:09:00
 * @LastEditTime: 2025-04-10 02:09:02
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.serialize.impl;

import com.weihua.rpc.common.exception.SerializeException;
import com.weihua.rpc.core.serialize.Serializer;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protobuf序列化器实现
 * 使用Protostuff进行序列化和反序列化
 */
public class ProtobufSerializer implements Serializer {

    /**
     * 序列化类型编号
     */
    private static final byte TYPE = 2;

    /**
     * 缓存Schema对象，避免重复创建
     */
    private static final Map<Class<?>, Schema<?>> SCHEMA_CACHE = new ConcurrentHashMap<>();

    @Override
    public byte[] serialize(Object obj) throws SerializeException {
        if (obj == null) {
            throw new SerializeException("序列化对象不能为空");
        }

        Class<?> clazz = obj.getClass();
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);

        try {
            Schema<Object> schema = getSchema(clazz);
            return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } catch (Exception e) {
            throw new SerializeException("Protobuf序列化失败: " + e.getMessage(), e);
        } finally {
            buffer.clear();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializeException {
        if (bytes == null || bytes.length == 0) {
            throw new SerializeException("反序列化的字节数组不能为空");
        }

        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            Schema<T> schema = getSchema(clazz);
            ProtostuffIOUtil.mergeFrom(bytes, instance, schema);
            return instance;
        } catch (Exception e) {
            throw new SerializeException("Protobuf反序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return "protobuf";
    }

    /**
     * 获取类的Schema
     */
    @SuppressWarnings("unchecked")
    private <T> Schema<T> getSchema(Class<?> clazz) {
        Schema<?> schema = SCHEMA_CACHE.get(clazz);
        if (schema == null) {
            schema = RuntimeSchema.createFrom(clazz);
            SCHEMA_CACHE.put(clazz, schema);
        }
        return (Schema<T>) schema;
    }
}
