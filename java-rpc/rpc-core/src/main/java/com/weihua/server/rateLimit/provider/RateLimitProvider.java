package com.weihua.server.rateLimit.provider;


import com.weihua.server.rateLimit.RateLimit;
import com.weihua.server.rateLimit.impl.TokenBucketRateLimitImpl;

import java.util.HashMap;
import java.util.Map;

public class RateLimitProvider {
    private Map<String, RateLimit> rateLimitMap = new HashMap<>();

    public RateLimit getRateLimit(String interfaceTime) {
        if (rateLimitMap.containsKey(interfaceTime)) {
            return rateLimitMap.get(interfaceTime);
        }
        RateLimit rateLimit = new TokenBucketRateLimitImpl(1, 10);
        rateLimitMap.put(interfaceTime, rateLimit);
        return rateLimit;
    }
}
