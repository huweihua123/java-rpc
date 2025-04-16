//package com.weihua.rpc.core.serialize;
//
//import com.weihua.rpc.core.serialize.impl.JsonSerializer;
//import com.weihua.rpc.core.serialize.impl.ProtobufSerializer;
//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//public class SerializerFactoryTest {
//
//    @Test
//    void testGetSerializerByName() {
//        Serializer jsonSerializer = SerializerFactory.getSerializer("json");
//        Serializer protobufSerializer = SerializerFactory.getSerializer("protobuf");
//
//        assertNotNull(jsonSerializer);
//        assertNotNull(protobufSerializer);
//        assertTrue(jsonSerializer instanceof JsonSerializer);
//        assertTrue(protobufSerializer instanceof ProtobufSerializer);
//    }
//
//    @Test
//    void testGetSerializerByNameCaseInsensitive() {
//        Serializer jsonSerializer1 = SerializerFactory.getSerializer("JSON");
//        Serializer jsonSerializer2 = SerializerFactory.getSerializer("Json");
//
//        assertNotNull(jsonSerializer1);
//        assertNotNull(jsonSerializer2);
//        assertTrue(jsonSerializer1 instanceof JsonSerializer);
//        assertTrue(jsonSerializer2 instanceof JsonSerializer);
//    }
//
//    @Test
//    void testGetSerializerByType() {
//        Serializer jsonSerializer = SerializerFactory.getSerializer((byte) 1);
//        Serializer protobufSerializer = SerializerFactory.getSerializer((byte) 2);
//
//        assertNotNull(jsonSerializer);
//        assertNotNull(protobufSerializer);
//        assertTrue(jsonSerializer instanceof JsonSerializer);
//        assertTrue(protobufSerializer instanceof ProtobufSerializer);
//    }
//
//    @Test
//    void testGetNonExistentSerializer() {
//        // 获取不存在的序列化器应该返回默认序列化器
//        Serializer nonExistent = SerializerFactory.getSerializer("nonexistent");
//        Serializer defaultSerializer = SerializerFactory.getDefaultSerializer();
//
//        assertNotNull(nonExistent);
//        assertNotNull(defaultSerializer);
//        assertSame(nonExistent, defaultSerializer);
//    }
//
//    @Test
//    void testGetNonExistentSerializerByType() {
//        // 获取不存在的序列化器类型应该返回默认序列化器
//        Serializer nonExistent = SerializerFactory.getSerializer((byte) 99);
//        Serializer defaultSerializer = SerializerFactory.getDefaultSerializer();
//
//        assertNotNull(nonExistent);
//        assertNotNull(defaultSerializer);
//        assertSame(nonExistent, defaultSerializer);
//    }
//
//    @Test
//    void testGetDefaultSerializer() {
//        Serializer defaultSerializer = SerializerFactory.getDefaultSerializer();
//
//        assertNotNull(defaultSerializer);
//        assertTrue(defaultSerializer instanceof JsonSerializer);
//    }
//
//    @Test
//    void testRegisterCustomSerializer() {
//        // 创建一个自定义序列化器
//        Serializer customSerializer = new Serializer() {
//            @Override
//            public byte[] serialize(Object obj) {
//                return new byte[0];
//            }
//
//            @Override
//            public <T> T deserialize(byte[] bytes, Class<T> clazz) {
//                return null;
//            }
//
//            @Override
//            public byte getType() {
//                return 10;
//            }
//
//            @Override
//            public String getName() {
//                return "custom";
//            }
//        };
//
//        SerializerFactory.registerSerializer(customSerializer);
//
//        Serializer retrieved = SerializerFactory.getSerializer("custom");
//        Serializer retrievedByType = SerializerFactory.getSerializer((byte) 10);
//
//        assertNotNull(retrieved);
//        assertNotNull(retrievedByType);
//        assertSame(customSerializer, retrieved);
//        assertSame(customSerializer, retrievedByType);
//    }
//}