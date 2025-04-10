/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:43:11
 * @LastEditTime: 2025-04-10 16:15:04
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring环境配置适配器
 * 将Spring的Environment适配为RPC框架的Configuration接口
 */
public class SpringConfigurationAdapter implements Configuration {

    private final Environment environment;
    private final String prefix;

    public SpringConfigurationAdapter(Environment environment) {
        this(environment, "rpc.");
    }

    public SpringConfigurationAdapter(Environment environment, String prefix) {
        this.environment = environment;
        this.prefix = prefix;
    }

    @Override
    public String getString(String key) {
        return environment.getProperty(addPrefixIfNeeded(key));
    }

    @Override
    public String getString(String key, String defaultValue) {
        return environment.getProperty(addPrefixIfNeeded(key), defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = getString(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
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
        String fullPrefix = addPrefixIfNeeded(prefix);

        // 修正: Environment接口没有getPropertyNames方法
        // 转换为ConfigurableEnvironment获取更多能力
        if (environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configurableEnvironment = (ConfigurableEnvironment) environment;

            // 遍历所有PropertySource
            for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
                if (propertySource instanceof EnumerablePropertySource) {
                    EnumerablePropertySource<?> enumerablePropertySource = (EnumerablePropertySource<?>) propertySource;

                    // 获取此属性源中的所有属性名称
                    for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                        if (propertyName.startsWith(fullPrefix)) {
                            // 获取属性值并添加到结果中
                            String value = environment.getProperty(propertyName);
                            if (value != null) {
                                result.put(propertyName.substring(fullPrefix.length()), value);
                            }
                        }
                    }
                }
            }
        } else {
            // 对于不可枚举的Environment，我们无法获取所有属性，记录警告
            // 可以添加日志: log.warn("Environment is not ConfigurableEnvironment, cannot
            // enumerate properties");
        }

        return result;
    }

    @Override
    public void setProperty(String key, String value) {
        // Spring Environment通常是只读的，无法直接设置属性
        // 这里可以记录日志或抛出异常
        throw new UnsupportedOperationException("Spring Environment is read-only");
    }

    private String addPrefixIfNeeded(String key) {
        return key.startsWith(prefix) ? key : prefix + key;
    }
}
