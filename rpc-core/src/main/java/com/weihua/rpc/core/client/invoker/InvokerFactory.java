package com.weihua.rpc.core.client.invoker;

import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.pool.InvokerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调用者工厂，管理和创建Invoker实例
 */
@Slf4j
@Component
public class InvokerFactory {

    @Autowired
    private InvokerManager invokerManager;

    @Autowired
    private ClientConfig clientConfig;

    // 按服务名存储所有Invoker
    private final Map<String, Map<InetSocketAddress, Invoker>> serviceInvokers = new ConcurrentHashMap<>();

    /**
     * 更新服务地址列表
     *
     * @param serviceName 服务名称
     * @param addresses   地址列表
     */
    public void updateServiceAddresses(String serviceName, List<InetSocketAddress> addresses) {
        // 获取服务的Invoker映射，如果不存在则创建
        Map<InetSocketAddress, Invoker> invokerMap = serviceInvokers.computeIfAbsent(
                serviceName, k -> new ConcurrentHashMap<>());

        // 添加新地址
        for (InetSocketAddress address : addresses) {
            if (!invokerMap.containsKey(address)) {
                try {
                    // 通过InvokerManager获取或创建Invoker
                    Invoker invoker = invokerManager.getInvoker(address);
                    invokerMap.put(address, invoker);
                    log.debug("为服务 {} 添加新的Invoker，地址: {}:{}",
                            serviceName, address.getHostString(), address.getPort());
                } catch (Exception e) {
                    log.error("为服务 {} 创建Invoker失败，地址: {}:{}, 原因: {}",
                            serviceName, address.getHostString(), address.getPort(), e.getMessage());
                }
            }
        }

        // 移除不再存在的地址
        List<InetSocketAddress> addressesToRemove = new ArrayList<>();
        for (InetSocketAddress existingAddress : invokerMap.keySet()) {
            if (!addresses.contains(existingAddress)) {
                addressesToRemove.add(existingAddress);
            }
        }

        for (InetSocketAddress address : addressesToRemove) {
            Invoker invoker = invokerMap.remove(address);
            log.info("从服务 {} 中移除不可用地址的Invoker: {}:{}",
                    serviceName, address.getHostString(), address.getPort());

            // 如果该地址不再被任何服务使用，则释放资源
            boolean stillInUse = false;
            for (Map<InetSocketAddress, Invoker> serviceMap : serviceInvokers.values()) {
                if (serviceMap.containsKey(address)) {
                    stillInUse = true;
                    break;
                }
            }

            if (!stillInUse && invoker != null) {
                invoker.destroy();
            }
        }
    }

    /**
     * 获取指定服务和地址列表的所有可用Invoker
     *
     * @param serviceName 服务名称
     * @param addresses   地址列表
     * @return Invoker列表
     */
    public List<Invoker> getInvokers(String serviceName, List<InetSocketAddress> addresses) {
        List<Invoker> result = new ArrayList<>();

        // 首先尝试从缓存获取
        Map<InetSocketAddress, Invoker> invokerMap = serviceInvokers.get(serviceName);
        if (invokerMap != null) {
            for (InetSocketAddress address : addresses) {
                Invoker invoker = invokerMap.get(address);
                if (invoker != null && invoker.isAvailable()) {
                    result.add(invoker);
                }
            }
        }

        // 如果缓存中没有足够的Invoker，则尝试创建新的
        if (result.isEmpty() && addresses != null && !addresses.isEmpty()) {
            for (InetSocketAddress address : addresses) {
                try {
                    Invoker invoker = invokerManager.getInvoker(address);
                    if (invoker != null && invoker.isAvailable()) {
                        // 更新缓存
                        if (invokerMap == null) {
                            invokerMap = new ConcurrentHashMap<>();
                            serviceInvokers.put(serviceName, invokerMap);
                        }
                        invokerMap.put(address, invoker);
                        result.add(invoker);
                    }
                } catch (Exception e) {
                    log.error("创建Invoker失败，服务: {}, 地址: {}:{}, 原因: {}",
                            serviceName, address.getHostString(), address.getPort(), e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 释放所有资源
     */
    @PreDestroy
    public void shutdown() {
        // 清空所有服务的Invoker映射
        serviceInvokers.clear();
        log.info("InvokerFactory已关闭，所有Invoker映射已清除");
    }
}
