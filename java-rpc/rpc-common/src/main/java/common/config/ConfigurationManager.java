/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-03 23:01:50
 * 
 * @LastEditTime: 2025-04-05 00:07:42
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
package common.config;

import lombok.extern.log4j.Log4j2;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class ConfigurationManager implements Configuration {
    // 单例实例
    private static volatile ConfigurationManager INSTANCE;

    // 应用角色：提供者或消费者
    public enum Role {
        PROVIDER, CONSUMER, COMMON
    }

    // 配置模式
    public enum ConfigMode {
        PROPERTIES_FIRST, // 优先使用属性文件配置（默认）
        YAML_FIRST, // 优先使用YAML配置
        YAML_ONLY // 仅使用YAML配置
    }

    // 当前配置模式
    private ConfigMode configMode = ConfigMode.PROPERTIES_FIRST;

    // 配置存储，按来源分类
    private final Map<String, Map<String, String>> configSources = new ConcurrentHashMap<>();

    // 合并后的配置
    private final Map<String, String> mergedConfig = new ConcurrentHashMap<>();

    // 配置优先级排序（高优先级在前）
    private static final String[] CONFIG_PRIORITIES = {
            "system", // 系统属性，最高优先级
            "env", // 环境变量
            "yaml", // YAML配置（新增）
            "provider", // 提供者配置
            "consumer", // 消费者配置
            "application", // 应用配置
            "core", // 核心配置
            "default" // 默认配置，最低优先级
    };

    // 配置文件路径
    private static final Map<String, String> CONFIG_FILES = new HashMap<>();

    static {
        CONFIG_FILES.put("default", "rpc-default.properties");
        CONFIG_FILES.put("core", "rpc-core.properties");
        CONFIG_FILES.put("consumer", "rpc-consumer.properties");
        CONFIG_FILES.put("provider", "rpc-provider.properties");
    }

    private ConfigurationManager() {
        // 初始化各配置源
        for (String source : CONFIG_PRIORITIES) {
            configSources.put(source, new ConcurrentHashMap<>());
        }

        // 加载系统属性（最高优先级）
        loadSystemProperties();

        // 加载环境变量
        loadEnvironmentVariables();

        // 加载默认配置文件（最低优先级）
        loadConfig("default");
    }

    /**
     * 扩展初始化方法，支持YAML配置
     */
    public void initialize(Role role, boolean useYaml) {
        // 如果使用YAML，则设置为YAML优先模式
        if (useYaml) {
            setConfigMode(ConfigMode.YAML_FIRST);
        }

        // 执行初始化
        initialize(role);
    }

    /**
     * 增强版初始化方法，支持指定配置模式
     * 
     * @param role 应用角色
     * @param mode 配置模式
     */
    public void initialize(Role role, ConfigMode mode) {
        // 设置配置模式
        setConfigMode(mode);

        // 执行初始化
        initialize(role);
    }

    /**
     * 初始化配置，应在应用启动时调用
     * 
     * @param role 应用角色（提供者或消费者）
     */
    public void initialize(Role role) {
        if (configMode == ConfigMode.YAML_ONLY) {
            // 仅加载YAML配置
            log.info("使用仅YAML配置模式，将只加载系统属性、环境变量和YAML配置");
            loadFromYaml(null);
        } else {
            // 首先加载默认配置
            if (configSources.get("default").isEmpty()) {
                loadConfig("default");
            }

            // 加载核心配置
            loadConfig("core");

            // 根据角色加载特定配置
            if (role == Role.PROVIDER) {
                loadConfig("provider");
                log.info("加载RPC提供者配置");
            } else if (role == Role.CONSUMER) {
                loadConfig("consumer");
                log.info("加载RPC消费者配置");
            }

            // 如果是YAML优先模式，加载YAML配置
            if (configMode == ConfigMode.YAML_FIRST) {
                loadFromYaml(null);
            }
        }

        // 重新合并配置以确保正确的优先级
        rebuildMergedConfig();

        log.info("RPC配置初始化完成。配置模式：{}，总配置项数量：{}", configMode, mergedConfig.size());
    }

    // 获取实例的方法改为懒加载模式
    public static ConfigurationManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ConfigurationManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ConfigurationManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 设置配置模式
     * 
     * @param mode 配置模式
     */
    public void setConfigMode(ConfigMode mode) {
        this.configMode = mode;
        log.info("配置模式已设置为：{}", mode);
    }

    /**
     * 获取当前配置模式
     */
    public ConfigMode getConfigMode() {
        return configMode;
    }

    /**
     * 加载指定类型的配置文件
     * 
     * @param configType 配置类型 (default, core, provider, consumer)
     */
    private void loadConfig(String configType) {
        String fileName = CONFIG_FILES.get(configType);
        if (fileName == null) {
            log.warn("Unknown config type: {}", configType);
            return;
        }

        loadPropertiesFromFile(fileName, configType);
    }

    private void loadSystemProperties() {
        Properties sysProps = System.getProperties();
        Map<String, String> systemConfig = configSources.get("system");

        sysProps.stringPropertyNames().forEach(key -> {
            if (key.startsWith("rpc.")) {
                systemConfig.put(key, sysProps.getProperty(key));
                mergedConfig.put(key, sysProps.getProperty(key));
            }
        });

        log.debug("Loaded {} system properties", systemConfig.size());
    }

    private void loadEnvironmentVariables() {
        Map<String, String> envConfig = configSources.get("env");
        Map<String, String> env = System.getenv();

        env.forEach((key, value) -> {
            // 处理环境变量，转换格式: RPC_XXX_YYY -> rpc.xxx.yyy
            if (key.startsWith("RPC_")) {
                String configKey = "rpc." + key.substring(4).toLowerCase().replace('_', '.');
                envConfig.put(configKey, value);
                mergedConfig.put(configKey, value);
            }
        });

        log.debug("Loaded {} environment variables", envConfig.size());
    }

    public void loadPropertiesFromFile(String fileName, String source) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);

                Map<String, String> sourceConfig = configSources.computeIfAbsent(source,
                        k -> new ConcurrentHashMap<>());

                props.stringPropertyNames().forEach(key -> {
                    sourceConfig.put(key, props.getProperty(key));
                });

                log.debug("Loaded {} properties from {}", props.size(), fileName);
            } else {
                log.warn("Configuration file not found: {}", fileName);
            }
        } catch (Exception e) {
            log.warn("Failed to load properties from {}", fileName, e);
        }
    }

    /**
     * 重新构建合并配置，确保正确的优先级
     */
    private void rebuildMergedConfig() {
        // 清空当前合并配置
        mergedConfig.clear();

        // 按优先级从低到高加载配置（CONFIG_PRIORITIES是从高到低排序的）
        for (int i = CONFIG_PRIORITIES.length - 1; i >= 0; i--) {
            String source = CONFIG_PRIORITIES[i];
            Map<String, String> sourceConfig = configSources.get(source);

            if (sourceConfig != null) {
                // 将该源的所有配置合并到最终配置中
                mergedConfig.putAll(sourceConfig);
            }
        }

        log.debug("Rebuilt merged configuration with {} items", mergedConfig.size());
    }

    @Override
    public String getString(String key) {
        return mergedConfig.get(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        return mergedConfig.getOrDefault(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = getString(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid integer value for key: {}, value: {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 获取double类型的配置值
     * 
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public double getDouble(String key, double defaultValue) {
        String value = getString(key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid double value for key: {}, value: {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 获取long类型的配置值
     * 
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public long getLong(String key, long defaultValue) {
        String value = getString(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid long value for key: {}, value: {}", key, value);
            }
        }
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    @Override
    public Map<String, String> getProperties(String prefix) {
        Map<String, String> result = new HashMap<>();
        mergedConfig.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                result.put(k, v);
            }
        });
        return result;
    }

    @Override
    public void setProperty(String key, String value) {
        // 设置为应用级配置
        Map<String, String> appConfig = configSources.get("application");
        appConfig.put(key, value);

        // 更新合并配置
        rebuildMergedConfig();
    }

    /**
     * 设置配置项
     */
    public void setConfig(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        // 保存到运行时配置中
        Map<String, String> runtimeConfig = configSources.computeIfAbsent("runtime", k -> new ConcurrentHashMap<>());
        runtimeConfig.put(key, value);
        log.debug("设置配置项: {} = {}", key, value);
    }

    /**
     * 从YAML文件加载配置
     */
    public void loadFromYaml(String configFile) {
        Map<String, String> yamlConfig = configSources.get("yaml");

        // 清空现有的YAML配置
        yamlConfig.clear();

        // 加载新的YAML配置
        int count = YamlConfigLoader.loadYamlConfig(this, configFile);

        // 重建合并配置以应用新的YAML配置
        rebuildMergedConfig();

        log.info("从YAML配置文件加载了{}个配置项", count);
    }

    /**
     * 提供从属性文件配置迁移至YAML配置的辅助方法
     * 
     * @return 当前所有配置的YAML格式表示
     */
    public String exportConfigAsYaml() {
        return YamlConfigLoader.exportConfigToYaml(mergedConfig);
    }

    /**
     * 保存当前配置到YAML文件（仅生成内容，不实际写入文件）
     * 
     * @return YAML格式的配置内容
     */
    public String generateYamlConfig() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# RPC配置 - 自动生成\n");
        yaml.append("# 生成时间: ").append(new java.util.Date()).append("\n\n");

        // 添加核心配置部分
        yaml.append("# 核心配置\n");
        Map<String, String> coreConfig = getProperties("rpc.core");
        for (Map.Entry<String, String> entry : coreConfig.entrySet()) {
            String key = entry.getKey().substring("rpc.core.".length());
            yaml.append(key).append(": ").append(entry.getValue()).append("\n");
        }
        yaml.append("\n");

        // 可以继续添加更多配置部分

        return yaml.toString();
    }

    /**
     * 重新加载所有配置
     * 用于运行时刷新配置
     */
    public void reload() {
        log.info("开始重新加载配置...");

        // 清空配置源，保留system和env
        for (String source : CONFIG_PRIORITIES) {
            if (!source.equals("system") && !source.equals("env")) {
                configSources.get(source).clear();
            }
        }

        // 重新加载默认配置
        loadConfig("default");

        // 加载核心配置
        loadConfig("core");

        // 检查并确定应用角色
        if (configSources.containsKey("provider") && !configSources.get("provider").isEmpty()) {
            loadConfig("provider");
            log.debug("重新加载了提供者配置");
        }

        if (configSources.containsKey("consumer") && !configSources.get("consumer").isEmpty()) {
            loadConfig("consumer");
            log.debug("重新加载了消费者配置");
        }

        // 重新合并配置
        rebuildMergedConfig();

        log.info("配置重新加载完成，当前共有 {} 个配置项", mergedConfig.size());

        // 可选：打印配置状态
        if (log.isDebugEnabled()) {
            dumpConfiguration();
        }
    }

    /**
     * 打印当前配置状态（用于调试）
     */
    public void dumpConfiguration() {
        log.info("==== Current Configuration Status ====");
        log.info("Total configuration items: {}", mergedConfig.size());

        for (String source : CONFIG_PRIORITIES) {
            Map<String, String> sourceConfig = configSources.get(source);
            if (sourceConfig != null && !sourceConfig.isEmpty()) {
                log.info("Source [{}] has {} items", source, sourceConfig.size());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("==== Configuration Details ====");
            mergedConfig.forEach((key, value) -> {
                log.debug("{} = {}", key, value);
            });
        }
    }
}