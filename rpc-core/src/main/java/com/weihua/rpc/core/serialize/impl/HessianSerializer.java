/*
 * @Author: weihua hu
 * @Date: 2025-04-15 02:14:04
 * @LastEditTime: 2025-04-15 02:14:05
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.serialize.impl;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.weihua.rpc.common.exception.SerializeException;
import com.weihua.rpc.core.serialize.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Hessian序列化器实现
 * 使用Hessian2进行二进制序列化，适合跨语言场景
 */
public class HessianSerializer implements Serializer {

    /**
     * 序列化类型编号
     */
    private static final byte TYPE = 4;

    @Override
    public byte[] serialize(Object obj) throws SerializeException {
        if (obj == null) {
            throw new SerializeException("序列化对象不能为空");
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Hessian2Output hessian2Output = new Hessian2Output(byteArrayOutputStream);

        try {
            hessian2Output.writeObject(obj);
            hessian2Output.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new SerializeException("Hessian序列化失败: " + e.getMessage(), e);
        } finally {
            try {
                hessian2Output.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializeException {
        if (bytes == null || bytes.length == 0) {
            throw new SerializeException("反序列化的字节数组不能为空");
        }

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        Hessian2Input hessian2Input = new Hessian2Input(byteArrayInputStream);

        try {
            return (T) hessian2Input.readObject(clazz);
        } catch (IOException e) {
            throw new SerializeException("Hessian反序列化失败: " + e.getMessage(), e);
        } finally {
            try {
                hessian2Input.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }
    }

    @Override
    public byte getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return "hessian";
    }
}
