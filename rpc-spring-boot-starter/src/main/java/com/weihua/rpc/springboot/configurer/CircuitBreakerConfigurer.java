package com.weihua.rpc.springboot.configurer;

import com.weihua.rpc.core.client.circuit.config.CircuitBreakerConfig;
import com.weihua.rpc.core.condition.ConditionalOnClientMode;
import com.weihua.rpc.springboot.properties.CircuitBreakerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 熔断器配置绑定器
 */
@Configuration
@ConditionalOnClientMode
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class CircuitBreakerConfigurer {

    /**
     * 创建熔断器配置对象
     * 
     * @param properties  熔断器配置属性
     * @param environment Spring环境，用于获取特定接口配置
     * @return 熔断器配置对象
     */
    @Bean
    public CircuitBreakerConfig circuitBreakerConfig(
            CircuitBreakerProperties properties,
            Environment environment) {

        return new CircuitBreakerConfig(
                environment,
                properties.getFailures(),
                properties.getSuccessRateThreshold(),
                properties.getResetTimeoutMs(),
                properties.getHalfOpenRequests());
    }
}