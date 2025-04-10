package com.weihua.rpc.core.client.registry.balance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnExpression("#{environment['rpc.mode'] == 'client'}")
public class LoadBalanceConfig {

    @Value("${rpc.load-balance.type:random}")
    private String loadBalanceType;

    @Bean("defaultLoadBalance")
    @Primary
    public LoadBalance defaultLoadBalance() {
        // 根据配置选择负载均衡器实现
        switch (loadBalanceType.toLowerCase()) {
            case "roundrobin":
                return new RoundRobinLoadBalance();
            case "leastactive":
                return new LeastActiveLoadBalance();
            case "consistenthash":
                return new ConsistentHashLoadBalance();
            case "random":
            default:
                return new RandomLoadBalance();
        }
    }
}