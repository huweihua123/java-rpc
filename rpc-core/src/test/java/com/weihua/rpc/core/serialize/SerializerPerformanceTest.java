//package com.weihua.rpc.core.serialize;
//
//import com.weihua.rpc.core.serialize.impl.JsonSerializer;
//import com.weihua.rpc.core.serialize.impl.ProtobufSerializer;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.*;
//
///**
// * 序列化性能测试
// * 比较不同序列化实现的性能差异
// */
//public class SerializerPerformanceTest {
//
//    private Serializer jsonSerializer;
//    private Serializer protobufSerializer;
//    private List<TestUser> smallList;
//    private List<TestUser> largeList;
//    private ComplexObject complexObject;
//
//    @BeforeEach
//    void setUp() {
//        jsonSerializer = new JsonSerializer();
//        protobufSerializer = new ProtobufSerializer();
//
//        // 准备小型测试数据
//        smallList = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            smallList.add(new TestUser(i, "User" + i, 20 + i % 50));
//        }
//
//        // 准备大型测试数据
//        largeList = new ArrayList<>();
//        for (int i = 0; i < 10000; i++) {
//            largeList.add(new TestUser(i, "User" + i, 20 + i % 50));
//        }
//
//        // 准备复杂对象测试数据
//        Map<String, Object> attributes = new HashMap<>();
//        attributes.put("string", "value");
//        attributes.put("int", 123);
//        attributes.put("boolean", true);
//        attributes.put("list", Arrays.asList(1, 2, 3, 4, 5));
//
//        complexObject = new ComplexObject();
//        complexObject.setId(1L);
//        complexObject.setName("ComplexObject");
//        complexObject.setUsers(smallList);
//        complexObject.setAttributes(attributes);
//        complexObject.setTags(new HashSet<>(Arrays.asList("tag1", "tag2", "tag3")));
//    }
//
//    @Test
//    void testSmallObjectPerformance() throws Exception {
//        TestUser user = new TestUser(1, "TestUser", 30);
//
//        // 预热
//        for (int i = 0; i < 1000; i++) {
//            jsonSerializer.serialize(user);
//            protobufSerializer.serialize(user);
//        }
//
//        System.out.println("==== 小对象序列化性能测试 ====");
//
//        // JSON序列化性能测试
//        long jsonStartTime = System.currentTimeMillis();
//        for (int i = 0; i < 100000; i++) {
//            byte[] data = jsonSerializer.serialize(user);
//            jsonSerializer.deserialize(data, TestUser.class);
//        }
//        long jsonEndTime = System.currentTimeMillis();
//
//        // Protobuf序列化性能测试
//        long protobufStartTime = System.currentTimeMillis();
//        for (int i = 0; i < 100000; i++) {
//            byte[] data = protobufSerializer.serialize(user);
//            protobufSerializer.deserialize(data, TestUser.class);
//        }
//        long protobufEndTime = System.currentTimeMillis();
//
//        System.out.println("JSON序列化+反序列化耗时: " + (jsonEndTime - jsonStartTime) + "ms");
//        System.out.println("Protobuf序列化+反序列化耗时: " + (protobufEndTime - protobufStartTime) + "ms");
//    }
//
//    @Test
//    void testListPerformance() throws Exception {
//        System.out.println("==== 列表序列化性能测试 (大小: " + largeList.size() + ") ====");
//
//        // JSON序列化性能测试
//        long jsonStartTime = System.currentTimeMillis();
//        byte[] jsonData = jsonSerializer.serialize(largeList);
//        jsonSerializer.deserialize(jsonData, List.class);
//        long jsonEndTime = System.currentTimeMillis();
//
//        // Protobuf序列化性能测试
//        long protobufStartTime = System.currentTimeMillis();
//        byte[] protobufData = protobufSerializer.serialize(largeList);
//        protobufSerializer.deserialize(protobufData, ArrayList.class);
//        long protobufEndTime = System.currentTimeMillis();
//
//        System.out.println("JSON序列化+反序列化耗时: " + (jsonEndTime - jsonStartTime) + "ms");
//        System.out.println("Protobuf序列化+反序列化耗时: " + (protobufEndTime - protobufStartTime) + "ms");
//        System.out.println("JSON序列化大小: " + jsonData.length + " bytes");
//        System.out.println("Protobuf序列化大小: " + protobufData.length + " bytes");
//    }
//
//    @Test
//    void testComplexObjectPerformance() throws Exception {
//        System.out.println("==== 复杂对象序列化性能测试 ====");
//
//        // JSON序列化性能测试
//        long jsonStartTime = System.currentTimeMillis();
//        for (int i = 0; i < 10000; i++) {
//            byte[] data = jsonSerializer.serialize(complexObject);
//            jsonSerializer.deserialize(data, ComplexObject.class);
//        }
//        long jsonEndTime = System.currentTimeMillis();
//
//        // Protobuf序列化性能测试
//        long protobufStartTime = System.currentTimeMillis();
//        for (int i = 0; i < 10000; i++) {
//            byte[] data = protobufSerializer.serialize(complexObject);
//            protobufSerializer.deserialize(data, ComplexObject.class);
//        }
//        long protobufEndTime = System.currentTimeMillis();
//
//        System.out.println("JSON序列化+反序列化耗时: " + (jsonEndTime - jsonStartTime) + "ms");
//        System.out.println("Protobuf序列化+反序列化耗时: " + (protobufEndTime - protobufStartTime) + "ms");
//    }
//
//    // 测试用的自定义类
//    public static class TestUser {
//        private int id;
//        private String name;
//        private int age;
//
//        public TestUser() {
//        }
//
//        public TestUser(int id, String name, int age) {
//            this.id = id;
//            this.name = name;
//            this.age = age;
//        }
//
//        // getters and setters
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
//    public static class ComplexObject {
//        private Long id;
//        private String name;
//        private List<TestUser> users;
//        private Map<String, Object> attributes;
//        private Set<String> tags;
//
//        public ComplexObject() {
//        }
//
//        // getters and setters
//        public Long getId() {
//            return id;
//        }
//
//        public void setId(Long id) {
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
//
//        public Map<String, Object> getAttributes() {
//            return attributes;
//        }
//
//        public void setAttributes(Map<String, Object> attributes) {
//            this.attributes = attributes;
//        }
//
//        public Set<String> getTags() {
//            return tags;
//        }
//
//        public void setTags(Set<String> tags) {
//            this.tags = tags;
//        }
//    }
//}