package com.weihua.client.cache;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.weihua.client.cache.config.CacheConfigManager;

/**
 * 服务缓存，存储服务名和地址映射关系
 * 使用读写锁提高并发性能
 */
@Log4j2
public class ServiceCache {
    // 使用ConcurrentHashMap提高并发性能
    private static final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    // 读写锁，提高并发性能
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // 缓存配置管理器
    private final CacheConfigManager cacheConfig;

    // 缓存清理调度器
    private final ScheduledExecutorService cleanupScheduler;

    public ServiceCache() {
        // 使用缓存配置管理器获取配置
        this.cacheConfig = CacheConfigManager.getInstance();

        // 设置定时清理任务
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "service-cache-cleanup");
            t.setDaemon(true);
            return t;
        });

        // 启动缓存清理任务
        int cleanupInterval = cacheConfig.getServiceCleanupInterval();
        if (cleanupInterval > 0) {
            cleanupScheduler.scheduleAtFixedRate(
                    this::cleanupExpiredEntries,
                    cleanupInterval,
                    cleanupInterval,
                    TimeUnit.SECONDS);
            log.info("服务缓存清理任务已启动，间隔: {}秒", cleanupInterval);
        }
    }

    /**
     * 清理过期条目
     * 当前实现简单输出日志，未来可以扩展为真正的过期清理
     */
    private void cleanupExpiredEntries() {
        log.debug("执行服务缓存清理任务，当前缓存服务数: {}, 实例数: {}",
                getServiceCount(), getInstanceCount());

        // TODO: 未来可以实现基于时间戳的真正过期清理
        // 例如:
        // 1. 修改缓存存储结构，记录每个条目的添加时间
        // 2. 遍历缓存条目，移除超过过期时间的条目
    }

    /**
     * 将服务实例添加到缓存
     * 
     * @return 如果是新增返回true，如果已存在返回false
     */
    public boolean addServcieToCache(String serviceName, String address) {
        rwLock.readLock().lock();
        try {
            List<String> addresslist = cache.computeIfAbsent(serviceName, k -> new ArrayList<>());

            // 检查地址是否已存在
            if (addresslist.contains(address)) {
                return false; // 地址已存在，不需要添加
            }
        } finally {
            rwLock.readLock().unlock();
        }

        // 需要添加新地址，获取写锁
        rwLock.writeLock().lock();
        try {
            List<String> addresslist = cache.computeIfAbsent(serviceName, k -> new ArrayList<>());
            // 再次检查，防止在获取写锁期间被其他线程修改
            if (addresslist.contains(address)) {
                return false;
            }

            // 检查缓存大小限制
            int maxSize = cacheConfig.getServiceMaxSize();
            if (getInstanceCount() >= maxSize) {
                log.warn("服务缓存已达到最大大小 {}, 无法添加更多实例", maxSize);
                return false;
            }

            addresslist.add(address);
            log.debug("将服务 {} 地址 {} 添加到本地缓存", serviceName, address);
            return true; // 成功添加新地址
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 替换服务地址
     */
    public void replaceServiceAddress(String serviceName, String oldAddress, String newAddress) {
        rwLock.writeLock().lock();
        try {
            List<String> addresslist = cache.get(serviceName);
            if (addresslist != null) {
                int old_index = addresslist.indexOf(oldAddress);
                if (old_index >= 0) {
                    addresslist.set(old_index, newAddress);
                    log.debug("服务 {} 地址更新: {} -> {}", serviceName, oldAddress, newAddress);
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取服务实例地址列表
     */
    public List<String> getServiceFromCache(String serviceName) {
        rwLock.readLock().lock();
        try {
            List<String> addresses = cache.get(serviceName);
            if (addresses != null && !addresses.isEmpty()) {
                // 返回副本，避免外部修改缓存内容
                return new ArrayList<>(addresses);
            }
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 从缓存中删除服务实例
     * 
     * @return 如果成功删除返回true，如果不存在返回false
     */
    public boolean delete(String serviceName, String address) {
        rwLock.writeLock().lock();
        try {
            List<String> addresslist = cache.get(serviceName);
            if (addresslist != null) {
                boolean removed = addresslist.remove(address);
                if (removed) {
                    log.debug("从缓存中移除服务 {} 地址 {}", serviceName, address);
                }
                return removed;
            }
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 移除指定服务的所有实例
     * 
     * @return 如果服务存在并被移除返回true，否则返回false
     */
    public boolean removeService(String serviceName) {
        rwLock.writeLock().lock();
        try {
            List<String> removed = cache.remove(serviceName);
            if (removed != null) {
                log.debug("已移除服务 {} 的所有实例，共 {} 个", serviceName, removed.size());
                return true;
            }
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 清空缓存
     */
    public void clearAll() {
        rwLock.writeLock().lock();
        try {
            cache.clear();
            log.info("服务缓存已清空");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取当前缓存的服务数量
     */
    public int getServiceCount() {
        rwLock.readLock().lock();
        try {
            return cache.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 获取当前缓存的实例总数
     */
    public int getInstanceCount() {
        rwLock.readLock().lock();
        try {
            return cache.values().stream()
                    .mapToInt(List::size)
                    .sum();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 关闭缓存，释放资源
     */
    public void shutdown() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            log.info("服务缓存清理任务已关闭");
        }
    }
}