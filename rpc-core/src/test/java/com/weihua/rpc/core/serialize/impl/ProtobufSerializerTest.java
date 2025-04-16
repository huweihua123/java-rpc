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
//public class ProtobufSerializerTest {
//
//    private Serializer serializer;
//
//    @BeforeEach
//    void setUp() {
//        serializer = new ProtobufSerializer();
//    }
//
//    @Test
//    void testGetType() {
//        assertEquals(2, serializer.getType());
//    }
//
//    @Test
//    void testGetName() {
//        assertEquals("protobuf", serializer.getName());
//    }
//
//    @Test
//    void testSerializeAndDeserializeCustomObject() throws SerializeException {
//        TestUser original = new TestUser(1, "Bob", 30);
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
//    void testSerializeComplexObject() throws SerializeException {
//        TestUser user1 = new TestUser(1, "User1", 20);
//        TestUser user2 = new TestUser(2, "User2", 30);
//        List<TestUser> users = Arrays.asList(user1, user2);
//
//        TestDepartment original = new TestDepartment(100, "Engineering", users);
//        byte[] serialized = serializer.serialize(original);
//        TestDepartment deserialized = serializer.deserialize(serialized, TestDepartment.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original.getId(), deserialized.getId());
//        assertEquals(original.getName(), deserialized.getName());
//        assertEquals(original.getUsers().size(), deserialized.getUsers().size());
//        assertEquals(original.getUsers().get(0).getName(), deserialized.getUsers().get(0).getName());
//        assertEquals(original.getUsers().get(1).getName(), deserialized.getUsers().get(1).getName());
//    }
//
//    @Test
//    void testSerializeNull() {
//        assertThrows(SerializeException.class, () -> serializer.serialize(null));
//    }
//
//    @Test
//    void testDeserializeEmpty() {
//        assertThrows(SerializeException.class, () -> serializer.deserialize(new byte[0], TestUser.class));
//    }
//
//    @Test
//    void testDeserializeInvalidData() {
//        byte[] invalidData = {1, 2, 3, 4, 5};
//        assertThrows(SerializeException.class, () -> serializer.deserialize(invalidData, TestUser.class));
//    }
//
//    @Test
//    void testSerializeWithEmptyCollection() throws SerializeException {
//        TestDepartment original = new TestDepartment(101, "HR", new ArrayList<>());
//        byte[] serialized = serializer.serialize(original);
//        TestDepartment deserialized = serializer.deserialize(serialized, TestDepartment.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original.getId(), deserialized.getId());
//        assertEquals(original.getName(), deserialized.getName());
//        assertEquals(0, deserialized.getUsers().size());
//    }
//
//    @Test
//    void testLargeObject() throws SerializeException {
//        List<TestUser> users = new ArrayList<>();
//        for (int i = 0; i < 1000; i++) {
//            users.add(new TestUser(i, "User" + i, 20 + i % 50));
//        }
//
//        TestDepartment original = new TestDepartment(200, "Large Department", users);
//        byte[] serialized = serializer.serialize(original);
//        TestDepartment deserialized = serializer.deserialize(serialized, TestDepartment.class);
//
//        assertNotNull(serialized);
//        assertTrue(serialized.length > 0);
//        assertEquals(original.getId(), deserialized.getId());
//        assertEquals(original.getName(), deserialized.getName());
//        assertEquals(original.getUsers().size(), deserialized.getUsers().size());
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
//
//    public static class TestDepartment {
//        private int id;
//        private String name;
//        private List<TestUser> users;
//
//        public TestDepartment() {
//        }
//
//        public TestDepartment(int id, String name, List<TestUser> users) {
//            this.id = id;
//            this.name = name;
//            this.users = users;
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
//        public List<TestUser> getUsers() {
//            return users;
//        }
//
//        public void setUsers(List<TestUser> users) {
//            this.users = users;
//        }
//    }
//}