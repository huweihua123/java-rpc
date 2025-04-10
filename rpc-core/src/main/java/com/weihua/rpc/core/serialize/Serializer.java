/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:08:20
 * @LastEditTime: 2025-04-10 02:08:21
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.serialize;

import com.weihua.rpc.common.exception.SerializeException;

/**
 * 序列化接口，定义序列化和反序列化方法
 */
public interface Serializer {

    /**
     * 序列化对象为字节数组
     * 
     * @param obj 待序列化的对象
     * @return 序列化后的字节数组
     * @throws SerializeException 序列化异常
     */
    byte[] serialize(Object obj) throws SerializeException;

    /**
     * 反序列化字节数组为对象
     * 
     * @param bytes 字节数组
     * @param clazz 目标类型
     * @return 反序列化后的对象
     * @throws SerializeException 反序列化异常
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializeException;

    /**
     * 获取序列化器类型
     * 
     * @return 序列化器类型编号
     */
    byte getType();

    /**
     * 获取序列化器名称
     * 
     * @return 序列化器名称
     */
    String getName();
}
