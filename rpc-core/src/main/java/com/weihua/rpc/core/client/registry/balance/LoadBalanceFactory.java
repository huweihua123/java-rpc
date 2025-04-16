/*
 * @Author: weihua hu
 * @Date: 2025-04-15 00:09:57
 * @LastEditTime: 2025-04-15 00:09:59
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.registry.balance;

import com.weihua.rpc.common.extension.ExtensionLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * 负载均衡工厂类
 * 使用SPI机制加载和管理负载均衡策略
 */
@Slf4j
public class LoadBalanceFactory {

    private static final ExtensionLoader<LoadBalance> LOADER = ExtensionLoader.getExtensionLoader(LoadBalance.class);

    /**
     * 获取负载均衡实现
     *
     * @param type 负载均衡类型，为空时返回默认实现
     * @return 负载均衡实现
     */
    public static LoadBalance getLoadBalance(String type) {
        if (type == null || type.isEmpty()) {
            return getDefaultLoadBalance();
        }

        try {
            if (LOADER.hasExtension(type)) {
                return LOADER.getExtension(type);
            } else {
                log.warn("未找到负载均衡实现: {}, 使用默认实现", type);
                return getDefaultLoadBalance();
            }
        } catch (Exception e) {
            log.error("加载负载均衡实现 {} 出错: {}", type, e.getMessage());
            return getDefaultLoadBalance();
        }
    }

    /**
     * 获取默认负载均衡实现
     *
     * @return 默认负载均衡实现
     */
    public static LoadBalance getDefaultLoadBalance() {
        return LOADER.getDefaultExtension();
    }
}