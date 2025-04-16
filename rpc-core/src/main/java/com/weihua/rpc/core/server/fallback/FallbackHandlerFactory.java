/*
 * @Author: weihua hu
 * @Date: 2025-04-16 18:39:17
 * @LastEditTime: 2025-04-16 18:39:19
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.fallback;

import com.weihua.rpc.common.extension.ExtensionLoader;
import com.weihua.rpc.core.server.annotation.RateLimit.FallbackStrategy;
import com.weihua.rpc.core.server.fallback.impl.DefaultValueFallbackHandler;
import com.weihua.rpc.core.server.fallback.impl.QueueFallbackHandler;
import com.weihua.rpc.core.server.fallback.impl.RejectFallbackHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 降级处理器工厂，用于创建和缓存降级处理器实例
 */
@Slf4j
public class FallbackHandlerFactory {

    // 策略处理器缓存
    private static final Map<FallbackStrategy, RateLimitFallbackHandler> HANDLERS_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取指定策略的降级处理器
     * 
     * @param strategy 降级策略
     * @return 降级处理器
     */
    public static RateLimitFallbackHandler getHandler(FallbackStrategy strategy) {
        return HANDLERS_CACHE.computeIfAbsent(strategy, FallbackHandlerFactory::createHandler);
    }

    /**
     * 创建指定策略的降级处理器
     */
    private static RateLimitFallbackHandler createHandler(FallbackStrategy strategy) {
        log.debug("创建降级处理器: 策略={}", strategy);

        // 首先尝试通过SPI机制加载
        try {
            String extensionName = strategy.name().toLowerCase();
            RateLimitFallbackHandler handler = ExtensionLoader.getExtensionLoader(RateLimitFallbackHandler.class)
                    .getExtension(extensionName);
            if (handler != null) {
                return handler;
            }
        } catch (Exception e) {
            log.warn("通过SPI加载降级处理器失败: {}, 使用默认实现", e.getMessage());
        }

        // 回退到默认实现
        switch (strategy) {
            case REJECT:
                return new RejectFallbackHandler();
            case QUEUE:
                return new QueueFallbackHandler();
            case RETURN_DEFAULT:
                return new DefaultValueFallbackHandler();
            default:
                log.warn("未知的降级策略: {}, 使用默认拒绝策略", strategy);
                return new RejectFallbackHandler();
        }
    }

    /**
     * 清除所有缓存的处理器
     */
    public static void clearAll() {
        HANDLERS_CACHE.clear();
        log.info("已清除所有降级处理器缓存");
    }
}