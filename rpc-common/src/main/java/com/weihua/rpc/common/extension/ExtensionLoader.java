package com.weihua.rpc.common.extension;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展加载器，参考Dubbo的扩展加载机制
 * 用于动态加载扩展实现
 *
 * @param <T> 扩展接口类型
 */
@Slf4j
public class ExtensionLoader<T> {

    private static final String SERVICES_DIRECTORY = "META-INF/services/";
    private static final String COMMENT_PREFIX = "#";

    // 扩展加载器缓存，key是扩展接口类，value是对应的加载器
    private static final Map<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();

    // 扩展实例缓存，key是扩展名，value是扩展实例
    private final Map<String, T> extensionInstances = new ConcurrentHashMap<>();
    
    // 扩展类缓存，key是扩展名，value是扩展类
    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    
    // 是否已初始化标记
    private volatile boolean initialized = false;

    // 扩展接口类
    private final Class<T> type;

    // 默认扩展名
    private String defaultExtensionName;

    private ExtensionLoader(Class<T> type) {
        this.type = type;
        // 从SPI注解中获取默认扩展名
        SPI spi = type.getAnnotation(SPI.class);
        if (spi != null) {
            this.defaultExtensionName = spi.value().trim();
        }
    }

    /**
     * 获取指定接口的扩展加载器
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("扩展类型不能为null");
        }

        if (!type.isInterface()) {
            throw new IllegalArgumentException("扩展类型必须是接口");
        }

        if (!type.isAnnotationPresent(SPI.class)) {
            throw new IllegalArgumentException("扩展类型" + type.getName() + "必须添加@SPI注解");
        }

        // 从缓存中获取，如果没有则创建
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    /**
     * 获取默认扩展实现
     */
    public T getDefaultExtension() {
        getExtensionClasses(); // 确保已加载
        
        if (defaultExtensionName == null || defaultExtensionName.length() == 0) {
            throw new IllegalStateException("接口 " + type.getName() + " 未指定默认扩展名");
        }
        return getExtension(defaultExtensionName);
    }

    /**
     * 根据名称获取扩展实现
     */
    public T getExtension(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("扩展名不能为空");
        }

        // 先确保类加载完成
        getExtensionClasses();
        
        // 从缓存中获取
        T instance = extensionInstances.get(name);
        if (instance == null) {
            synchronized (extensionInstances) {
                instance = extensionInstances.get(name);
                if (instance == null) {
                    // 检查扩展是否存在
                    Class<?> clazz = cachedClasses.get(name);
                    if (clazz == null) {
                        throw new IllegalStateException("扩展 " + name + " 未找到，可用扩展: " + cachedClasses.keySet());
                    }
                    instance = createExtension(name);
                    extensionInstances.put(name, instance);
                }
            }
        }

        return instance;
    }

    /**
     * 检查扩展名是否存在
     */
    public boolean hasExtension(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        try {
            return getExtensionClasses().containsKey(name);
        } catch (Exception e) {
            log.error("检查扩展是否存在时发生错误", e);
            return false;
        }
    }

    /**
     * 获取所有扩展实现
     */
    public Map<String, T> getExtensions() {
        Map<String, Class<?>> extensionClasses = getExtensionClasses();
        Map<String, T> extensions = new HashMap<>(extensionClasses.size());

        for (Map.Entry<String, Class<?>> entry : extensionClasses.entrySet()) {
            try {
                extensions.put(entry.getKey(), getExtension(entry.getKey()));
            } catch (Exception e) {
                log.error("加载扩展 {} 失败", entry.getKey(), e);
                // 跳过加载失败的扩展
            }
        }

        return extensions;
    }

    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        Class<?> clazz = cachedClasses.get(name);
        if (clazz == null) {
            throw new IllegalStateException("扩展 " + name + " 未找到");
        }

        try {
            T instance = (T) clazz.newInstance();
            log.debug("创建扩展实例成功: {}", name);
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException("扩展 " + name + " 实例化失败", e);
        }
    }

    private Map<String, Class<?>> getExtensionClasses() {
        // 检查是否已初始化，避免重复加载
        if (!initialized) {
            synchronized (cachedClasses) {
                if (!initialized) {
                    loadExtensionClasses(cachedClasses);
                    initialized = true;
                    log.debug("接口 {} 的扩展加载完成，共加载 {} 个扩展", type.getName(), cachedClasses.size());
                }
            }
        }
        return cachedClasses;
    }

    private void loadExtensionClasses(Map<String, Class<?>> extensionClasses) {
        String fileName = SERVICES_DIRECTORY + type.getName();
        int loadedCount = 0;
        
        try {
            ClassLoader classLoader = findClassLoader();
            Enumeration<URL> urls = classLoader.getResources(fileName);

            if (urls != null) {
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    loadedCount += loadResource(extensionClasses, classLoader, url);
                }
            }
            
            log.debug("从路径 {} 加载了 {} 个扩展", fileName, loadedCount);
        } catch (Exception e) {
            log.error("加载扩展类失败: {}", type.getName(), e);
            throw new IllegalStateException("加载扩展类失败: " + type.getName(), e);
        }
    }
    
    private ClassLoader findClassLoader() {
        ClassLoader cl = null;
        
        // 尝试使用线程上下文类加载器
        cl = Thread.currentThread().getContextClassLoader();
        
        if (cl == null) {
            // 回退到当前类的类加载器
            cl = ExtensionLoader.class.getClassLoader();
            
            if (cl == null) {
                // 最后尝试使用系统类加载器
                cl = ClassLoader.getSystemClassLoader();
            }
        }
        
        return cl;
    }

    private int loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, URL url) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 处理注释和空行
                final int ci = line.indexOf(COMMENT_PREFIX);
                if (ci >= 0) {
                    line = line.substring(0, ci);
                }
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }

                int i = line.indexOf('=');
                String name;
                String className;
                if (i > 0) {
                    // name=class 格式
                    name = line.substring(0, i).trim();
                    className = line.substring(i + 1).trim();
                } else {
                    // 只有class，使用小写类名短名作为扩展名
                    className = line;
                    int lastDotIndex = className.lastIndexOf('.');
                    name = className.substring(lastDotIndex + 1).toLowerCase();
                }

                if (name.length() > 0 && className.length() > 0) {
                    try {
                        Class<?> clazz = classLoader.loadClass(className);
                        if (!type.isAssignableFrom(clazz)) {
                            log.error("扩展 {} 不是 {} 的实现", clazz.getName(), type.getName());
                            throw new IllegalStateException("扩展 " + clazz.getName() + " 不是 " + type.getName() + " 的实现");
                        }
                        
                        // 如果已存在同名扩展，记录警告日志
                        if (extensionClasses.containsKey(name)) {
                            log.warn("扩展名 {} 已被 {} 占用，将被 {} 覆盖",
                                    name, extensionClasses.get(name).getName(), className);
                        }
                        
                        extensionClasses.put(name, clazz);
                        count++;
                        log.debug("加载扩展: {}={}", name, className);
                    } catch (ClassNotFoundException e) {
                        log.error("扩展类 {} 未找到: {}", className, e.getMessage());
                        throw new IllegalStateException("扩展类 " + className + " 未找到", e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("从 {} 加载扩展资源失败", url, e);
            throw new IllegalStateException("扩展资源加载失败: " + url, e);
        }
        return count;
    }
}