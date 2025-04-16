/*
 * @Author: weihua hu
 * @Date: 2025-04-15 02:13:54
 * @LastEditTime: 2025-04-15 15:13:01
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.serialize.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.weihua.rpc.common.exception.SerializeException;
import com.weihua.rpc.core.serialize.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Kryo序列化器实现
 * 使用Kryo进行高效的Java对象序列化和反序列化
 */
public class KryoSerializer implements Serializer {

    /**
     * 序列化类型编号
     */
    private static final byte TYPE = 3;

    /**
     * 使用ThreadLocal确保线程安全
     */
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 不要求预先注册类
        kryo.setReferences(true); // 支持循环引用
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) throws SerializeException {
        if (obj == null) {
            throw new SerializeException("序列化对象不能为空");
        }

        Kryo kryo = KRYO_THREAD_LOCAL.get();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);

        try {
            kryo.writeObject(output, obj);
            output.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new SerializeException("Kryo序列化失败: " + e.getMessage(), e);
        } finally {
            output.close();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializeException {
        if (bytes == null || bytes.length == 0) {
            throw new SerializeException("反序列化的字节数组不能为空");
        }

        Kryo kryo = KRYO_THREAD_LOCAL.get();
        Input input = new Input(new ByteArrayInputStream(bytes));

        try {
            return kryo.readObject(input, clazz);
        } catch (Exception e) {
            throw new SerializeException("Kryo反序列化失败: " + e.getMessage(), e);
        } finally {
            input.close();
        }
    }

    @Override
    public byte getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return "kryo";
    }
}