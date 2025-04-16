/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:32:46
 * @LastEditTime: 2025-04-16 22:13:28
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.spring.config;

import com.weihua.rpc.core.client.cache.DefaultServiceAddressCache;
import com.weihua.rpc.core.client.cache.ServiceAddressCache;
import com.weihua.rpc.core.client.circuit.CircuitBreakerProvider;
import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.config.DiscoveryConfig;
import com.weihua.rpc.core.client.netty.NettyRpcClient;
import com.weihua.rpc.core.client.invoker.InvokerManager;
import com.weihua.rpc.core.client.proxy.ClientProxyFactory;
import com.weihua.rpc.core.client.registry.ServiceDiscovery;
import com.weihua.rpc.core.client.registry.ServiceDiscoveryFactory;
import com.weihua.rpc.core.client.registry.balance.LoadBalance;
import com.weihua.rpc.core.client.registry.balance.LoadBalanceFactory;

import com.weihua.rpc.core.client.retry.DefaultRetryPolicy;
import com.weihua.rpc.core.client.retry.RetryPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RPC客户端配置类
 * 使用显式Bean定义代替组件扫描
 */
@Configuration
public class RpcClientConfiguration {

    /**
     * 客户端配置初始化完成后的日志
     */
    public RpcClientConfiguration() {
        System.out.println("RPC客户端配置已初始化");
    }

    /**
     * 熔断器提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerProvider circuitBreakerProvider() {
        return new CircuitBreakerProvider();
    }

    /**
     * 服务地址缓存
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceAddressCache serviceAddressCache() {
        return new DefaultServiceAddressCache();
    }

    @Bean
    public RetryPolicy retryPolicy(ClientConfig clientConfig) {
        return new DefaultRetryPolicy(clientConfig);
    }

    /**
     * 连接管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public InvokerManager invokerManager() {
        return new InvokerManager();
    }

    /**
     * 客户端代理工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientProxyFactory clientProxyFactory() {
        return new ClientProxyFactory();
    }

    /**
     * 负载均衡器 - 从工厂获取，替代之前的多个条件Bean
     */
    @Bean
    @ConditionalOnMissingBean(LoadBalance.class)
    public LoadBalance loadBalance(ClientConfig clientConfig) {
        // 从客户端配置获取负载均衡类型
        String loadBalanceType = clientConfig.getLoadBalanceStrategy();
        return LoadBalanceFactory.getLoadBalance(loadBalanceType);
    }

    @Bean
    @ConditionalOnMissingBean(ServiceDiscovery.class)
    public ServiceDiscovery serviceDiscovery(
            DiscoveryConfig discoveryConfig,
            ServiceAddressCache addressCache,
            InvokerManager invokerManager) {
        return ServiceDiscoveryFactory.getServiceDiscovery(
                discoveryConfig, addressCache, invokerManager);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public NettyRpcClient nettyRpcClient(
            ClientConfig clientConfig,
            ServiceDiscovery serviceDiscovery,
            LoadBalance loadBalance) {
        return new NettyRpcClient(clientConfig, serviceDiscovery, loadBalance);
    }
}