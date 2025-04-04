/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-03 20:27:04
 * 
 * @LastEditTime: 2025-04-04 23:04:46
 * 
 * @LastEditors: weihua hu
 * 
 * @Description: 类似Dubbo的SPI扩展加载器
 */
package common.spi;

import common.config.ConfigurationManager;
import common.config.URL;
import common.spi.annotation.Adaptive;
import common.spi.annotation.SPI;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 扩展点加载器，支持配置文件驱动的SPI机制
 * 
 * @param <T> 扩展点类型
 */
@Log4j2
public class ExtensionLoader<T> {
    // SPI配置目录
    private static final String SERVICES_DIRECTORY = "META-INF/services/";
    private static final String RPC_DIRECTORY = "META-INF/rpc/";
    private static final String CONFIG_DIRECTORY = "META-INF/config/";

    // 扩展名称正则校验
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]+");

    // 扩展加载器缓存，key为扩展点类型
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();

    // 扩展实例缓存，key为扩展名
    private final ConcurrentMap<String, Holder<T>> cachedInstances = new ConcurrentHashMap<>();

    // 扩展实现类缓存，key为扩展名
    private final Map<String, Class<?>> extensionClasses = new HashMap<>();

    // 自适应实例缓存
    private final Holder<T> cachedAdaptiveInstance = new Holder<>();

    // 扩展点接口类
    private final Class<?> type;

    // 默认扩展名
    private String defaultExtensionName;

    // 辅助类，用于持有扩展实例
    private static class Holder<T> {
        private volatile T value;
    }

    private ExtensionLoader(Class<?> type) {
        this.type = type;

        // 加载默认扩展名
        SPI spi = type.getAnnotation(SPI.class);
        if (spi != null) {
            this.defaultExtensionName = spi.value();
        }
    }

    /**
     * 获取扩展加载器
     * 
     * @param type 扩展点类型
     * @param <T>  泛型类型
     * @return 扩展加载器
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }

        // 必须是接口
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }

        // 必须标记@SPI注解
        if (!type.isAnnotationPresent(SPI.class)) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not a SPI interface!");
        }

        // 从缓存中获取，没有则创建
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }

        return loader;
    }

    /**
     * 获取扩展点实现
     * 
     * @param name 扩展名，为null则获取默认扩展
     * @return 扩展实现实例
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultExtension();
        }

        if ("true".equals(name)) {
            return getDefaultExtension();
        }

        // 从缓存中获取
        Holder<T> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }

        T instance = holder.value;
        if (instance == null) {
            synchronized (holder) {
                instance = holder.value;
                if (instance == null) {
                    instance = createExtension(name);
                    holder.value = instance;
                }
            }
        }

        return instance;
    }

    /**
     * 获取默认扩展实现
     * 
     * @return 默认扩展实现
     */
    public T getDefaultExtension() {
        getExtensionClasses(); // 确保加载了扩展类

        if (defaultExtensionName == null || defaultExtensionName.isEmpty()) {
            throw new IllegalStateException("No default extension on extension " + type.getName());
        }
        return getExtension(defaultExtensionName);
    }

    /**
     * 创建扩展实例
     * 
     * @param name 扩展名
     * @return 扩展实例
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        // 加载所有扩展类
        Map<String, Class<?>> extensionClasses = getExtensionClasses();

        // 查找指定名称的扩展类
        Class<?> clazz = extensionClasses.get(name);
        if (clazz == null) {
            throw new IllegalStateException(
                    "Extension instance (name: " + name + ", class: " + type + ") could not be found");
        }

        try {
            // 创建实例
            T instance = (T) clazz.newInstance();
            log.debug("Created extension instance: {}, class: {}", name, clazz.getName());

            // 注入依赖
            instance = injectExtension(instance);

            return instance;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Extension instance (name: " + name + ", class: " + clazz + ") could not be instantiated", e);
        }
    }

    /**
     * 给扩展实例注入依赖
     * 简化实现，实际Dubbo中有更复杂的依赖注入机制
     */
    private T injectExtension(T instance) {
        try {
            if (instance != null) {
                // 这里可以实现依赖注入，例如对带有@Inject注解的字段注入依赖
                // 简化实现，实际应该根据字段类型和注解进行注入
            }
        } catch (Exception e) {
            log.error("Failed to inject dependencies for extension instance: " + instance.getClass(), e);
        }
        return instance;
    }

    /**
     * 获取所有扩展实现类
     * 
     * @return 扩展实现类映射
     */
    private Map<String, Class<?>> getExtensionClasses() {
        // 已加载则直接返回
        if (!extensionClasses.isEmpty()) {
            return extensionClasses;
        }

        synchronized (extensionClasses) {
            if (!extensionClasses.isEmpty()) {
                return extensionClasses;
            }

            // 加载扩展实现类
            loadExtensionClasses();
            return extensionClasses;
        }
    }

    /**
     * 获取自适应扩展点，会根据URL参数动态选择实现
     */
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.value;
        if (instance == null) {
            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.value;
                if (instance == null) {
                    instance = createAdaptiveExtension();
                    cachedAdaptiveInstance.value = (T) instance;
                }
            }
        }

        return (T) instance;
    }

    /**
     * 创建自适应扩展点实例
     */
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            // 获取或创建自适应扩展类
            Class<?> adaptiveClass = getAdaptiveExtensionClass();
            T instance = (T) adaptiveClass.newInstance();
            return injectExtension(instance);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create adaptive extension for " + type.getName(), e);
        }
    }

    /**
     * 获取自适应扩展类
     */
    private Class<?> getAdaptiveExtensionClass() {
        // 确保加载了扩展类
        getExtensionClasses();

        // 查找带@Adaptive注解的实现类
        for (Map.Entry<String, Class<?>> entry : extensionClasses.entrySet()) {
            Class<?> clazz = entry.getValue();
            if (clazz.isAnnotationPresent(Adaptive.class)) {
                log.debug("Found adaptive extension: {}", clazz.getName());
                return clazz;
            }
        }

        // 没有找到带@Adaptive注解的类，则使用默认实现
        // 实际上应该生成动态代理类，简化处理
        log.debug("No adaptive extension found, using default extension: {}", defaultExtensionName);
        return extensionClasses.get(defaultExtensionName);
    }

    /**
     * 加载所有扩展实现类
     */
    private void loadExtensionClasses() {
        // 获取SPI注解默认值
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation != null) {
            String value = defaultAnnotation.value();

            // 尝试从配置中获取覆盖值
            String configKey = "rpc." + type.getSimpleName().toLowerCase().replace(".", "_");
            String configValue = ConfigurationManager.getInstance().getString(configKey);

            if (configValue != null && !configValue.isEmpty()) {
                value = configValue;
                log.info("Override default extension [{}] from config: {} -> {}",
                        type.getName(), defaultAnnotation.value(), value);
            }

            defaultExtensionName = value;
        }

        // 加载指定路径下的扩展点配置
        loadDirectory(SERVICES_DIRECTORY); // 兼容Java SPI
        loadDirectory(RPC_DIRECTORY); // 自定义RPC扩展目录
        loadDirectory(CONFIG_DIRECTORY); // 配置文件目录
    }

    /**
     * 加载指定目录下的扩展点配置
     */
    private void loadDirectory(String dir) {
        String fileName = dir + type.getName();

        try {
            ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
            Enumeration<java.net.URL> urls = classLoader.getResources(fileName);

            if (urls != null) {
                while (urls.hasMoreElements()) {
                    java.net.URL url = urls.nextElement();
                    loadResource(url);
                }
            }
        } catch (Exception e) {
            log.error("Exception when loading extension class (interface: " + type + ", dir: " + dir + ")", e);
        }
    }

    /**
     * 加载资源文件
     * 
     * @param url 资源URL
     */
    private void loadResource(java.net.URL url) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 去除注释和空白
                final int ci = line.indexOf('#');
                if (ci >= 0) {
                    line = line.substring(0, ci);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 解析名称=实现类
                int i = line.indexOf('=');
                String name = null;
                String clazzName;

                if (i > 0) {
                    name = line.substring(0, i).trim();
                    clazzName = line.substring(i + 1).trim();
                } else {
                    clazzName = line;
                }

                if (clazzName.isEmpty() || (name != null && !NAME_PATTERN.matcher(name).matches())) {
                    log.warn("Invalid SPI configuration: {}", line);
                    continue;
                }

                try {
                    // 加载类
                    Class<?> clazz = Class.forName(clazzName, true, ExtensionLoader.class.getClassLoader());

                    // 检查类型是否匹配
                    if (!type.isAssignableFrom(clazz)) {
                        throw new IllegalStateException("Error when load extension class (interface: " + type
                                + ", class: " + clazzName + "), class " + clazzName + " is not subtype of interface.");
                    }

                    // 存入扩展类映射
                    if (name == null || name.isEmpty()) {
                        name = clazz.getSimpleName();
                        // 转换为小驼峰
                        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                    }

                    // 如果已存在同名扩展，则检查优先级
                    if (extensionClasses.containsKey(name)) {
                        if (extensionClasses.get(name) != clazz) {
                            log.warn("Duplicate extension name {} on {}", name, type.getName());
                        }
                    } else {
                        extensionClasses.put(name, clazz);
                    }

                    log.debug("Loaded extension: {}, class: {}", name, clazzName);
                } catch (ClassNotFoundException e) {
                    log.error("Failed to load extension class (interface: " + type + ", class: " + clazzName + ")", e);
                }
            }
        } catch (Exception e) {
            log.error("Exception when loading extension resources (interface: " + type + ", url: " + url + ")", e);
        }
    }

    /**
     * 检查方法是否是自适应方法
     * 
     * @param method 方法
     * @return 是否是自适应方法
     */
    private boolean isAdaptiveMethod(final Method method) {
        if (method.isAnnotationPresent(Adaptive.class)) {
            return true;
        }

        // 默认情况下，接口中的方法如果有URL参数，也认为是自适应的
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class<?> parameterType : parameterTypes) {
            if (URL.class.equals(parameterType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取所有扩展名
     * 
     * @return 扩展名集合
     */
    public Set<String> getSupportedExtensions() {
        getExtensionClasses();
        return new HashSet<>(extensionClasses.keySet());
    }

    /**
     * 检查扩展是否存在
     * 
     * @param name 扩展名
     * @return 是否存在
     */
    public boolean hasExtension(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        try {
            getExtensionClasses();
            return extensionClasses.containsKey(name);
        } catch (Exception e) {
            log.error("Exception when checking extension " + name, e);
            return false;
        }
    }

    /**
     * 获取当前默认扩展名
     * 
     * @return 默认扩展名
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return defaultExtensionName;
    }
}