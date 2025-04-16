//package com.weihua.rpc.core.serialize.impl;
//
//import com.weihua.rpc.common.exception.SerializeException;
//import com.weihua.rpc.core.serialize.Serializer;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.*;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//public class JsonSerializerTest {
//
//    private Serializer serializer;
//
//    @BeforeEach
//    void setUp() {
//        serializer = new JsonSerializer();
//    }
//
//    @Test
//    void testGetType() {
//        assertEquals(1, serializer.getType());
//    }
//
//    @Test
//    void testGetName() {
//        assertEquals("json", serializer.getName());
//    }
//
//    @Test
//    void testSerializeAndDeserializeString() throws SerializeException {
//        String original = "Hello, RPC!";
//        byte[] serialized = serializer.serialize(original);
//        String deserialized = serializer.deserialize(serialized, String.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original, deserialized);
//    }
//
//    @Test
//    void testSerializeAndDeserializeInteger() throws SerializeException {
//        Integer original = 42;
//        byte[] serialized = serializer.serialize(original);
//        Integer deserialized = serializer.deserialize(serialized, Integer.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original, deserialized);
//    }
//
//    @Test
//    void testSerializeAndDeserializeList() throws SerializeException {
//        List<String> original = Arrays.asList("item1", "item2", "item3");
//        byte[] serialized = serializer.serialize(original);
//        List<?> deserialized = serializer.deserialize(serialized, List.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original.size(), deserialized.size());
//        assertEquals(original, deserialized);
//    }
//
//    @Test
//    void testSerializeAndDeserializeMap() throws SerializeException {
//        Map<String, Object> original = new HashMap<>();
//        original.put("key1", "value1");
//        original.put("key2", 123);
//        original.put("key3", Arrays.asList("nested1", "nested2"));
//
//        byte[] serialized = serializer.serialize(original);
//        Map<?, ?> deserialized = serializer.deserialize(serialized, Map.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original.size(), deserialized.size());
//        assertEquals(original.get("key1"), deserialized.get("key1"));
//        assertEquals(original.get("key2").toString(), deserialized.get("key2").toString()); // FastJSON可能将数字转为不同类型
//        assertEquals(original.get("key3"), deserialized.get("key3"));
//    }
//
//    @Test
//    void testSerializeAndDeserializeCustomObject() throws SerializeException {
//        TestUser original = new TestUser(1, "Alice", 25);
//        byte[] serialized = serializer.serialize(original);
//        TestUser deserialized = serializer.deserialize(serialized, TestUser.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original.getId(), deserialized.getId());
//        assertEquals(original.getName(), deserialized.getName());
//        assertEquals(original.getAge(), deserialized.getAge());
//    }
//
//    @Test
//    void testSerializeNull() {
//        assertThrows(SerializeException.class, () -> serializer.serialize(null));
//    }
//
//    @Test
//    void testDeserializeEmpty() {
//        assertThrows(SerializeException.class, () -> serializer.deserialize(new byte[0], String.class));
//    }
//
//    @Test
//    void testDeserializeInvalidData() {
//        byte[] invalidData = { 1, 2, 3, 4, 5 };
//        assertThrows(SerializeException.class, () -> serializer.deserialize(invalidData, String.class));
//    }
//
//    @Test
//    void testSerializeEmptyCollection() throws SerializeException {
//        List<String> emptyList = new ArrayList<>();
//        byte[] serialized = serializer.serialize(emptyList);
//        List<?> deserialized = serializer.deserialize(serialized, List.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(0, deserialized.size());
//    }
//
//    // 测试用的自定义类
//    public static class TestUser {
//        private int id;
//        private String name;
//        private int age;
//
//        // 必须有默认构造函数，否则反序列化可能失败
//        public TestUser() {
//        }
//
//        public TestUser(int id, String name, int age) {
//            this.id = id;
//            this.name = name;
//            this.age = age;
//        }
//
//        public int getId() {
//            return id;
//        }
//
//        public void setId(int id) {
//            this.id = id;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public void setName(String name) {
//            this.name = name;
//        }
//
//        public int getAge() {
//            return age;
//        }
//
//        public void setAge(int age) {
//            this.age = age;
//        }
//    }
//}