/*
 * @Author: weihua hu
 * @Date: 2025-03-19 19:02:55
 * @LastEditTime: 2025-04-06 19:35:47
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.client.serverCenter.impl;

import com.weihua.client.cache.ServiceCache;
import com.weihua.client.config.RegistryConfigManager;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.balance.LoadBalance;
import com.weihua.client.serverCenter.handler.ServiceAddressChangeHandler;
import com.weihua.client.serverCenter.watch.WatchZk;
import common.message.RpcRequest;
import common.spi.ExtensionLoader;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

@Log4j2
public class ZkServiceCenter implements ServiceCenter {
    // 配置管理器
    private final RegistryConfigManager configManager;
    private final String RETRY = "CanRetry";
    // zookeeper客户端
    private CuratorFramework client;
    private ServiceCache cache;
    private WatchZk watcher;
    private LoadBalance loadBalance;
    private Set<String> retryServiceCache = new CopyOnWriteArraySet<>();
    
    // 地址变更事件监听器
    private final Map<String, Set<Consumer<List<String>>>> addressChangeListeners = new ConcurrentHashMap<>();
    // 服务路径监听缓存
    private final Map<String, PathChildrenCache> pathCacheMap = new ConcurrentHashMap<>();
    // 地址变更处理器
    private final ServiceAddressChangeHandler addressChangeHandler;

    public ZkServiceCenter() {
        // 使用注册中心配置管理器
        this.configManager = RegistryConfigManager.getInstance();
        this.addressChangeHandler = ServiceAddressChangeHandler.getInstance();

        // 指数时间重试
        RetryPolicy policy = new ExponentialBackoffRetry(
                configManager.getZkBaseSleepTime(),
                configManager.getZkMaxRetries());

        // 使用配置管理器获取ZooKeeper配置
        String zkAddress = configManager.getZkAddress();
        int sessionTimeout = configManager.getZkSessionTimeout();
        int connectTimeout = configManager.getZkConnectionTimeout();
        String rootPath = configManager.getZkRootPath();

        log.info("初始化ZooKeeper服务中心: 地址={}, 会话超时={}ms, 连接超时={}ms, 根路径={}",
                zkAddress, sessionTimeout, connectTimeout, rootPath);

        this.client = CuratorFrameworkFactory.builder()
                .connectString(zkAddress)
                .sessionTimeoutMs(sessionTimeout)
                .connectionTimeoutMs(connectTimeout)
                .retryPolicy(policy)
                .namespace(rootPath)
                .build();

        this.client.start();
        log.info("ZooKeeper客户端连接成功");

        this.cache = new ServiceCache();
        this.watcher = new WatchZk(client, cache);

        // 使用配置管理器获取负载均衡策略
        String loadBalanceStrategy = configManager.getLoadBalanceStrategy();
        this.loadBalance = ExtensionLoader
                .getExtensionLoader(LoadBalance.class)
                .getExtension(loadBalanceStrategy);
        log.info("使用负载均衡策略: {}", loadBalanceStrategy);

        // 监听根路径变化
        watcher.watchToUpdate(rootPath);
    }

    @Override
    public boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature) {
        if (retryServiceCache.isEmpty()) {
            try {
                CuratorFramework rootClient = client.usingNamespace(RETRY);
                List<String> retryableMethods = rootClient.getChildren()
                        .forPath("/" + getServiceAddress(serviceAddress));
                retryServiceCache.addAll(retryableMethods);
            } catch (Exception e) {
                log.info("检查重试失败，方法签名：{}", methodSignature, e);
            }
        }
        return retryServiceCache.contains(methodSignature);
    }

    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest rpcRequest) {
        String serviceName = rpcRequest.getInterfaceName();

        List<String> addressList = cache.getServiceFromCache(serviceName);

        try {
            if (addressList == null || addressList.isEmpty()) {
                // 从ZK获取地址列表
                addressList = client.getChildren().forPath("/" + serviceName);

                // 更新缓存
                if (addressList != null && !addressList.isEmpty()) {
                    // 清除旧缓存
                    List<String> oldAddresses = cache.getServiceFromCache(serviceName);
                    if (oldAddresses != null) {
                        for (String old : oldAddresses) {
                            cache.delete(serviceName, old);
                        }
                    }
                    
                    // 添加新地址到缓存
                    for (String address : addressList) {
                        cache.addServcieToCache(serviceName, address);
                    }
                    
                    // 通知地址变更处理器
                    addressChangeHandler.handleAddressChange(serviceName, addressList);
                }
            }

            if (addressList == null || addressList.isEmpty()) {
                log.warn("未找到服务：{}", serviceName);
                return null;
            }
            
            // 使用负载均衡选择一个地址
            String address = loadBalance.balance(addressList);
            return parseAddress(address);
        } catch (Exception e) {
            log.error("服务发现失败，服务名：{}", serviceName, e);
        }
        return null;
    }

    @Override
    public void subscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName == null || listener == null) {
            return;
        }
        
        // 将监听器添加到集合
        Set<Consumer<List<String>>> listeners = addressChangeListeners.computeIfAbsent(
                serviceName, k -> ConcurrentHashMap.newKeySet());
        listeners.add(listener);
        
        // 如果还没有为该服务路径设置监听，则创建
        if (!pathCacheMap.containsKey(serviceName)) {
            try {
                // 创建路径监听
                String path = "/" + serviceName;
                
                // 确保路径存在
                try {
                    if (client.checkExists().forPath(path) == null) {
                        client.create().creatingParentsIfNeeded().forPath(path);
                    }
                } catch (Exception e) {
                    log.warn("尝试创建路径失败: {}", path, e);
                }
                
                PathChildrenCache pathCache = new PathChildrenCache(client, path, true);
                pathCache.getListenable().addListener((client, event) -> {
                    if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED ||
                            event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED ||
                            event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
                        
                        // 获取最新的子节点列表
                        List<String> currentAddresses = new ArrayList<>();
                        try {
                            List<String> children = client.getChildren().forPath(path);
                            if (children != null) {
                                currentAddresses.addAll(children);
                            }
                        } catch (Exception e) {
                            log.error("获取节点子列表失败: {}", path, e);
                        }
                        
                        // 更新缓存
                        updateCache(serviceName, currentAddresses);
                        
                        // 通知所有监听器
                        notifyAddressChange(serviceName, currentAddresses);
                    }
                });
                
                // 启动监听
                pathCache.start();
                pathCacheMap.put(serviceName, pathCache);
                
                log.info("已为服务 {} 创建地址变更监听", serviceName);
                
                // 立即获取一次当前地址并通知
                List<String> currentAddresses = client.getChildren().forPath(path);
                notifyAddressChange(serviceName, currentAddresses);
                
            } catch (Exception e) {
                log.error("为服务创建地址变更监听失败: {}", serviceName, e);
            }
        }
    }
    
    @Override
    public void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName == null || listener == null) {
            return;
        }
        
        // 从监听器集合中移除
        Set<Consumer<List<String>>> listeners = addressChangeListeners.get(serviceName);
        if (listeners != null) {
            listeners.remove(listener);
            
            // 如果没有监听器了，关闭路径监听
            if (listeners.isEmpty()) {
                PathChildrenCache pathCache = pathCacheMap.remove(serviceName);
                if (pathCache != null) {
                    try {
                        pathCache.close();
                        log.info("关闭服务 {} 的地址变更监听", serviceName);
                    } catch (Exception e) {
                        log.error("关闭地址变更监听失败: {}", serviceName, e);
                    }
                }
                
                // 移除监听器集合
                addressChangeListeners.remove(serviceName);
            }
        }
    }
    
    /**
     * 更新本地缓存
     */
    private void updateCache(String serviceName, List<String> currentAddresses) {
        try {
            // 清除旧缓存
            List<String> oldAddresses = cache.getServiceFromCache(serviceName);
            if (oldAddresses != null) {
                for (String old : oldAddresses) {
                    cache.delete(serviceName, old);
                }
            }
            
            // 添加新地址到缓存
            if (currentAddresses != null) {
                for (String address : currentAddresses) {
                    cache.addServcieToCache(serviceName, address);
                }
            }
        } catch (Exception e) {
            log.error("更新缓存失败: {}", serviceName, e);
        }
    }
    
    /**
     * 通知地址变更
     */
    private void notifyAddressChange(String serviceName, List<String> currentAddresses) {
        // 通知地址变更处理器
        addressChangeHandler.handleAddressChange(serviceName, currentAddresses);
        
        // 通知注册的监听器
        Set<Consumer<List<String>>> listeners = addressChangeListeners.get(serviceName);
        if (listeners != null) {
            for (Consumer<List<String>> listener : listeners) {
                try {
                    listener.accept(currentAddresses);
                } catch (Exception e) {
                    log.error("通知地址变更监听器失败", e);
                }
            }
        }
    }

    // 地址 -> XXX.XXX.XXX.XXX:port 字符串
    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() +
                ":" +
                serverAddress.getPort();
    }

    // 字符串解析为地址
    private InetSocketAddress parseAddress(String address) {
        String[] result = address.split(":");
        return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
    }

    @Override
    public void close() {
        // 关闭所有PathChildrenCache
        for (PathChildrenCache pathCache : pathCacheMap.values()) {
            try {
                pathCache.close();
            } catch (Exception e) {
                log.error("关闭PathChildrenCache失败", e);
            }
        }
        pathCacheMap.clear();
        
        // 清空监听器
        addressChangeListeners.clear();
        
        // 关闭client
        if (client != null) {
            client.close();
        }
    }
}
