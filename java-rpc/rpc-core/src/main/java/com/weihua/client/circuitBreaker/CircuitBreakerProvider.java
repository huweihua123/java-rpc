package com.weihua.client.circuitBreaker;

import java.util.HashMap;
import java.util.Map;

public class CircuitBreakerProvider {
    private Map<String, CircuitBreaker> circuitBreakerMap = new HashMap<>();

    public CircuitBreaker getCircuitBreaker(String interfaceName) {
        CircuitBreaker circuitBreaker = circuitBreakerMap.getOrDefault(interfaceName, new CircuitBreaker(
                5,      // 5次失败触发熔断
                0.8,    // 80%成功率要求
                30000,  // 30秒熔断持续时间
                10      // 半开状态最多10次探测请求
        ));

        circuitBreakerMap.put(interfaceName, circuitBreaker);

        return circuitBreaker;

    }
}
