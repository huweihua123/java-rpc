/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:08:42
 * @LastEditTime: 2025-04-10 02:08:43
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.serialize.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.weihua.rpc.common.exception.SerializeException;
import com.weihua.rpc.core.serialize.Serializer;

/**
 * JSON序列化器实现
 * 使用FastJSON进行序列化和反序列化
 */
public class JsonSerializer implements Serializer {

    /**
     * 序列化类型编号
     */
    private static final byte TYPE = 1;

    @Override
    public byte[] serialize(Object obj) throws SerializeException {
        try {
            return JSON.toJSONBytes(obj,
                    SerializerFeature.WriteClassName,
                    SerializerFeature.DisableCircularReferenceDetect);
        } catch (Exception e) {
            throw new SerializeException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializeException {
        try {
            return JSON.parseObject(bytes, clazz, Feature.SupportAutoType);
        } catch (Exception e) {
            throw new SerializeException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return "json";
    }
}
