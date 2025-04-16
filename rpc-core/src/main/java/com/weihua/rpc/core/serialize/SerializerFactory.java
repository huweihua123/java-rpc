package com.weihua.rpc.core.serialize;

import com.weihua.rpc.common.extension.ExtensionLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器工厂，支持SPI机制和配置文件选择
 */
@Slf4j
public class SerializerFactory {

    // 按类型缓存序列化器，懒加载
    private static final Map<Byte, Serializer> SERIALIZERS_BY_TYPE = new ConcurrentHashMap<>();

    // 扩展加载器
    private static final ExtensionLoader<Serializer> LOADER = ExtensionLoader.getExtensionLoader(Serializer.class);

    // 初始化状态和当前配置的序列化器
    private static volatile boolean initialized = false;
    private static volatile Serializer configuredSerializer = null;

    /**
     * 根据序列化类型初始化
     * 
     * @param type 序列化类型名称
     */
    public static synchronized void initFromType(String type) {
        // 避免重复初始化
        if (initialized) {
            log.debug("序列化器已初始化为 {}, 跳过重复初始化",
                    configuredSerializer != null ? configuredSerializer.getName() : "默认值");
            return;
        }

        if (StringUtils.hasText(type)) {
            String serializerType = type.toLowerCase();
            try {
                if (LOADER.hasExtension(serializerType)) {
                    configuredSerializer = LOADER.getExtension(serializerType);
                    log.info("已初始化序列化器: {} (类型代码: {})",
                            configuredSerializer.getName(), configuredSerializer.getType());
                    initialized = true;
                } else {
                    log.warn("序列化器类型 '{}' 不存在, 将使用默认序列化器", type);
                    configuredSerializer = LOADER.getDefaultExtension();
                    initialized = true;
                }
            } catch (Exception e) {
                log.error("初始化序列化器 '{}' 失败: {}", type, e.getMessage());
                log.warn("将使用默认序列化器");
                configuredSerializer = LOADER.getDefaultExtension();
                initialized = true;
            }
        } else {
            // 使用默认值
            log.info("未指定序列化类型，将使用默认序列化器");
            configuredSerializer = LOADER.getDefaultExtension();
            initialized = true;
        }

        // 预缓存当前配置的序列化器类型
        if (configuredSerializer != null) {
            SERIALIZERS_BY_TYPE.put(configuredSerializer.getType(), configuredSerializer);
        }
    }

    /**
     * 获取默认序列化器，优先使用配置中指定的序列化器
     */
    public static Serializer getDefaultSerializer() {
        if (!initialized) {
            synchronized (SerializerFactory.class) {
                if (!initialized) {
                    // 如果没有初始化，则使用SPI默认值
                    configuredSerializer = LOADER.getDefaultExtension();
                    initialized = true;
                    log.info("自动初始化默认序列化器: {}", configuredSerializer.getName());
                }
            }
        }
        return configuredSerializer;
    }

    /**
     * 根据名称获取序列化器
     */
    public static Serializer getSerializer(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultSerializer();
        }

        try {
            return LOADER.getExtension(name.toLowerCase());
        } catch (Exception e) {
            log.warn("获取序列化器 '{}' 失败: {}, 将使用默认序列化器", name, e.getMessage());
            return getDefaultSerializer();
        }
    }

    /**
     * 根据类型获取序列化器
     */
    public static Serializer getSerializer(byte type) {
        // 从类型缓存中获取
        Serializer serializer = SERIALIZERS_BY_TYPE.get(type);
        if (serializer != null) {
            return serializer;
        }

        // 类型未缓存，尝试查找并缓存
        synchronized (SERIALIZERS_BY_TYPE) {
            serializer = SERIALIZERS_BY_TYPE.get(type);
            if (serializer != null) {
                return serializer;
            }

            // 遍历所有序列化器查找匹配的类型
            for (Serializer s : LOADER.getExtensions().values()) {
                if (s.getType() == type) {
                    SERIALIZERS_BY_TYPE.put(type, s);
                    log.debug("缓存序列化器类型: {} -> {}", type, s.getName());
                    return s;
                }
            }

            // 未找到匹配的类型，返回默认序列化器
            log.warn("未找到类型为 {} 的序列化器，将使用默认序列化器", type);
            return getDefaultSerializer();
        }
    }

    /**
     * 获取所有可用的序列化器
     */
    public static Map<String, Serializer> getAllSerializers() {
        return LOADER.getExtensions();
    }

    /**
     * 重置初始化状态 (主要用于测试)
     */
    public static synchronized void reset() {
        initialized = false;
        configuredSerializer = null;
        SERIALIZERS_BY_TYPE.clear();
        log.debug("序列化器工厂已重置");
    }
}