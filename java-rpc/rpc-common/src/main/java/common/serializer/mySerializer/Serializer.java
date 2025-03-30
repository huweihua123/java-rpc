package common.serializer.mySerializer;

import java.util.HashMap;
import java.util.Map;

public interface Serializer {
    /*
     * 1、 首先得有序列化方法
     * 2、得有反序列化方法
     * */

    static final Map<Integer, Serializer> serializerMap = new HashMap<>();

    static Serializer getSerializerByType(int code) {
        if (serializerMap.isEmpty()) {
            serializerMap.put(1, new JsonSerializer());
        }
        return serializerMap.get(code);
    }

    byte[] serialize(Object object);

    Object deSerialize(byte[] bytes, int messageType);

    int getType();

}
