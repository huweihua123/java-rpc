package com.weihua.server.rateLimit.provider;

import com.weihua.server.rateLimit.RateLimit;
import com.weihua.server.rateLimit.config.RateLimitConfigManager;
import com.weihua.server.rateLimit.impl.TokenBucketRateLimitImpl;
import common.config.ConfigRefreshManager;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流器提供者
 * 单例模式实现
 */
@Log4j2
public class RateLimitProvider implements ConfigRefreshManager.ConfigurableComponent {
    // 单例实例
    private static volatile RateLimitProvider instance;

    private final Map<String, RateLimit> rateLimitMap = new ConcurrentHashMap<>();
    private final RateLimitConfigManager configManager;

    /**
     * 获取RateLimitProvider单例实例
     * 
     * @return RateLimitProvider实例
     */
    public static RateLimitProvider getInstance() {
        if (instance == null) {
            synchronized (RateLimitProvider.class) {
                if (instance == null) {
                    instance = new RateLimitProvider();
                    log.info("初始化RateLimitProvider单例");
                }
            }
        }
        return instance;
    }

    /**
     * 私有构造函数，防止外部实例化
     */
    private RateLimitProvider() {
        this.configManager = RateLimitConfigManager.getInstance();
        // 注册到配置刷新管理器
        ConfigRefreshManager.getInstance().register(this);
    }

    /**
     * 获取指定接口的限流器
     * 
     * @param interfaceName 接口名称
     * @return 限流器实例
     */
    public RateLimit getRateLimit(String interfaceName) {
        // 使用computeIfAbsent保证原子性操作
        return rateLimitMap.computeIfAbsent(interfaceName, key -> {
            // 从配置管理器获取接口限流配置
            RateLimitConfigManager.InterfaceRateLimit limitConfig = configManager.getInterfaceRateLimit(key);

            log.info("为接口 {} 创建限流器 - 速率: {}ms/令牌, 容量: {}",
                    key, limitConfig.getRateMs(), limitConfig.getCapacity());

            return new TokenBucketRateLimitImpl(limitConfig.getRateMs(), limitConfig.getCapacity());
        });
    }

    /**
     * 实现ConfigurableComponent接口的配置刷新方法
     */
    @Override
    public void refreshConfig() {
        // 更新现有的限流器配置
        for (Map.Entry<String, RateLimit> entry : rateLimitMap.entrySet()) {
            String interfaceName = entry.getKey();
            TokenBucketRateLimitImpl rateLimit = (TokenBucketRateLimitImpl) entry.getValue();

            // 从配置管理器获取最新配置
            RateLimitConfigManager.InterfaceRateLimit limitConfig = configManager.getInterfaceRateLimit(interfaceName);

            // 更新限流器参数
            rateLimit.updateConfig(limitConfig.getRateMs(), limitConfig.getCapacity());
            log.info("已更新接口 {} 的限流配置 - 速率: {}ms/令牌, 容量: {}",
                    interfaceName, limitConfig.getRateMs(), limitConfig.getCapacity());
        }

        log.info("限流配置刷新完成");
    }

}