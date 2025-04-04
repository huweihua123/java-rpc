/*
 * @Author: weihua hu
 * @Date: 2025-04-05 00:06:46
 * @LastEditTime: 2025-04-05 00:27:14
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.config;

import lombok.extern.log4j.Log4j2;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * YAML配置加载器
 * 用于加载YAML格式的配置文件，并转换为ConfigurationManager支持的形式
 */
@Log4j2
public class YamlConfigLoader {
    private static final String DEFAULT_CONFIG_FILE = "rpc.yaml";

    /**
     * 加载YAML配置文件到ConfigurationManager
     * 
     * @param configManager 配置管理器
     * @param configFile    配置文件名，如果为null则使用默认文件名
     * @return 加载的配置项数量
     */
    public static int loadYamlConfig(ConfigurationManager configManager, String configFile) {
        String filename = (configFile != null && !configFile.isEmpty()) ? configFile : DEFAULT_CONFIG_FILE;
        log.info("加载YAML配置文件: {}", filename);

        try (InputStream inputStream = YamlConfigLoader.class.getClassLoader().getResourceAsStream(filename)) {
            if (inputStream == null) {
                log.warn("未找到YAML配置文件: {}", filename);
                return 0;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(inputStream);

            // 转换YAML配置为平面属性
            Map<String, String> flattenedConfig = new HashMap<>();
            flattenYamlMap(config, "", flattenedConfig);

            // 将配置添加到ConfigurationManager
            flattenedConfig.forEach((key, value) -> {
                // 将破折号转换为点，确保键格式一致
                String normalizedKey = key.startsWith("rpc.") ? key : "rpc." + key.replace('-', '.');
                configManager.setConfig(normalizedKey, value);
            });

            log.info("从YAML加载了{}个配置项", flattenedConfig.size());
            return flattenedConfig.size();
        } catch (IOException e) {
            log.error("加载YAML配置文件失败: {}", filename, e);
            return 0;
        }
    }

    /**
     * 将当前配置导出为YAML格式
     * 
     * @param config 当前配置
     * @return YAML格式的配置字符串
     */
    public static String exportConfigToYaml(Map<String, String> config) {
        // 将平面配置转换为嵌套结构
        Map<String, Object> nestedConfig = new HashMap<>();

        // 处理每个配置项
        config.forEach((key, value) -> {
            // 如果是以rpc.开头的配置项，去掉前缀
            String processedKey = key.startsWith("rpc.") ? key.substring(4) : key;

            // 将点分隔的键转换为嵌套结构
            String[] parts = processedKey.split("\\.");
            Map<String, Object> current = nestedConfig;

            // 处理嵌套结构
            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new HashMap<>());
            }

            // 设置最终值
            current.put(parts[parts.length - 1], value);
        });

        // 配置YAML输出选项
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        // 导出为YAML
        Yaml yaml = new Yaml(options);
        return yaml.dump(nestedConfig);
    }

    /**
     * 递归平铺YAML配置对象
     * 
     * @param map    YAML配置对象
     * @param prefix 当前键前缀
     * @param result 结果集
     */
    @SuppressWarnings("unchecked")
    private static void flattenYamlMap(Map<String, Object> map, String prefix, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String newPrefix = prefix.isEmpty() ? key : prefix + "." + key;

            if (value instanceof Map) {
                // 递归处理嵌套Map
                flattenYamlMap((Map<String, Object>) value, newPrefix, result);
            } else if (value instanceof List) {
                // 处理列表
                processList((List<Object>) value, newPrefix, result);
            } else {
                // 基本类型值
                result.put(newPrefix, value != null ? value.toString() : "");
            }
        }
    }

    /**
     * 处理YAML中的列表类型
     */
    @SuppressWarnings("unchecked")
    private static void processList(List<Object> list, String prefix, Map<String, String> result) {
        // 存储列表大小
        result.put(prefix + ".size", Integer.toString(list.size()));

        // 对于特殊的服务列表和引用列表，处理方式不同
        if (prefix.equals("services")) {
            processServicesList(list, result);
        } else if (prefix.equals("references")) {
            processReferencesList(list, result);
        } else {
            // 一般列表处理
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                String itemPrefix = prefix + "[" + i + "]";

                if (item instanceof Map) {
                    flattenYamlMap((Map<String, Object>) item, itemPrefix, result);
                } else {
                    result.put(itemPrefix, item != null ? item.toString() : "");
                }
            }
        }
    }

    /**
     * 处理服务列表
     */
    @SuppressWarnings("unchecked")
    private static void processServicesList(List<Object> servicesList, Map<String, String> result) {
        for (int i = 0; i < servicesList.size(); i++) {
            if (!(servicesList.get(i) instanceof Map))
                continue;

            Map<String, Object> service = (Map<String, Object>) servicesList.get(i);
            String interfaceName = (String) service.get("interface");

            if (interfaceName != null) {
                // 存储服务接口名称
                result.put("services[" + i + "].interface", interfaceName);

                // 存储实现类
                if (service.containsKey("implementation")) {
                    result.put("services[" + i + "].implementation", (String) service.get("implementation"));
                }

                // 存储版本
                if (service.containsKey("version")) {
                    result.put("services[" + i + "].version", service.get("version").toString());
                }

                // 存储分组
                if (service.containsKey("group")) {
                    result.put("services[" + i + "].group", (String) service.get("group"));
                }
            }
        }
    }

    /**
     * 处理服务引用列表
     */
    private static void processReferencesList(List<Object> referencesList, Map<String, String> result) {
        StringBuilder refs = new StringBuilder();
        for (int i = 0; i < referencesList.size(); i++) {
            Object item = referencesList.get(i);
            if (item != null) {
                if (refs.length() > 0) {
                    refs.append(",");
                }
                refs.append(item.toString());
                result.put("references[" + i + "]", item.toString());
            }
        }
        // 存储引用列表字符串
        result.put("references", refs.toString());
    }

    /**
     * 创建一个包含常见RPC配置模板的YAML文件内容
     * 用于初始化配置
     * 
     * @return YAML模板字符串
     */
    public static String createRpcYamlTemplate() {
        StringBuilder template = new StringBuilder();
        template.append("# RPC配置模板\n");
        template.append("# 创建时间: ").append(new Date()).append("\n\n");

        // 应用配置部分
        template.append("# 应用配置\n");
        template.append("application:\n");
        template.append("  name: my-rpc-application\n");
        template.append("  version: 1.0.0\n\n");

        // 注册中心配置
        template.append("# 注册中心配置\n");
        template.append("registry:\n");
        template.append("  address: zookeeper://127.0.0.1:2181\n");
        template.append("  timeout: 3000\n");
        template.append("  retry: 3\n\n");

        // 协议配置
        template.append("# 协议配置\n");
        template.append("protocol:\n");
        template.append("  name: rpc\n");
        template.append("  port: 20880\n");
        template.append("  serialization: json\n\n");

        // 提供者配置
        template.append("# 服务提供者配置\n");
        template.append("provider:\n");
        template.append("  timeout: 5000\n");
        template.append("  threads: 200\n");
        template.append("  connections: 100\n\n");

        // 消费者配置
        template.append("# 服务消费者配置\n");
        template.append("consumer:\n");
        template.append("  timeout: 3000\n");
        template.append("  retries: 2\n");
        template.append("  loadbalance: random\n\n");

        // 服务配置
        template.append("# 服务配置\n");
        template.append("services:\n");
        template.append("  - interface: com.example.DemoService\n");
        template.append("    implementation: com.example.DemoServiceImpl\n");
        template.append("    version: 1.0.0\n");
        template.append("    group: demo\n\n");

        // 引用配置
        template.append("# 服务引用配置\n");
        template.append("references:\n");
        template.append("  - com.example.RemoteService\n");

        return template.toString();
    }
}
