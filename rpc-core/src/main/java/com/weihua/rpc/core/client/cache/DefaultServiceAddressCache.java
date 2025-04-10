package com.weihua.rpc.core.client.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * 服务地址缓存默认实现
 */
@Slf4j
@Component
public class DefaultServiceAddressCache implements ServiceAddressCache {

    // 服务地址缓存
    private final Map<String, List<String>> addressCache = new ConcurrentHashMap<>();

    // 地址变更监听器
    private final Map<String, Set<Consumer<List<String>>>> addressChangeListeners = new ConcurrentHashMap<>();

    // 服务下线监听器
    private final Map<String, Set<Runnable>> serviceUnavailableListeners = new ConcurrentHashMap<>();

    // 读写锁，保护缓存操作
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    @Override
    public List<String> getAddresses(String serviceName) {
        if (serviceName == null) {
            return Collections.emptyList();
        }

        rwLock.readLock().lock();
        try {
            List<String> addresses = addressCache.get(serviceName);
            return addresses != null ? new ArrayList<>(addresses) : Collections.emptyList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void updateAddresses(String serviceName, List<String> addresses) {
        if (serviceName == null) {
            return;
        }

        List<String> oldAddresses;
        boolean becameUnavailable = false;

        rwLock.writeLock().lock();
        try {
            oldAddresses = addressCache.get(serviceName);

            // 检查服务是否从可用变为不可用
            if ((oldAddresses != null && !oldAddresses.isEmpty()) &&
                    (addresses == null || addresses.isEmpty())) {
                becameUnavailable = true;
            }

            // 更新缓存
            if (addresses != null && !addresses.isEmpty()) {
                addressCache.put(serviceName, new ArrayList<>(addresses));
            } else if (addresses == null || addresses.isEmpty()) {
                // 如果地址为空，从缓存中移除，这样下次获取时会触发同步
                addressCache.remove(serviceName);
            }
        } finally {
            rwLock.writeLock().unlock();
        }

        // 通知地址变更
        if (!Objects.equals(oldAddresses, addresses)) {
            notifyAddressChange(serviceName, addresses != null ? addresses : Collections.emptyList());
        }

        // 如果服务变为不可用，通知监听器
        if (becameUnavailable) {
            notifyServiceUnavailable(serviceName);
        }
    }

    @Override
    public void subscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName == null || listener == null) {
            return;
        }

        Set<Consumer<List<String>>> listeners = addressChangeListeners.computeIfAbsent(
                serviceName, k -> ConcurrentHashMap.newKeySet());
        listeners.add(listener);

        // 立即通知当前地址
        List<String> currentAddresses = getAddresses(serviceName);
        listener.accept(currentAddresses);
    }

    @Override
    public void unsubscribeAddressChange(String serviceName, Consumer<List<String>> listener) {
        if (serviceName == null || listener == null) {
            return;
        }

        Set<Consumer<List<String>>> listeners = addressChangeListeners.get(serviceName);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                addressChangeListeners.remove(serviceName);
            }
        }
    }

    @Override
    public boolean isServiceAvailable(String serviceName) {
        if (serviceName == null) {
            return false;
        }

        rwLock.readLock().lock();
        try {
            List<String> addresses = addressCache.get(serviceName);
            return addresses != null && !addresses.isEmpty();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void addServiceUnavailableListener(String serviceName, Runnable listener) {
        if (serviceName == null || listener == null) {
            return;
        }

        Set<Runnable> listeners = serviceUnavailableListeners.computeIfAbsent(
                serviceName, k -> ConcurrentHashMap.newKeySet());
        listeners.add(listener);
    }

    /**
     * 通知地址变更
     */
    private void notifyAddressChange(String serviceName, List<String> addresses) {
        Set<Consumer<List<String>>> listeners = addressChangeListeners.get(serviceName);
        if (listeners != null) {
            for (Consumer<List<String>> listener : listeners) {
                try {
                    listener.accept(addresses);
                } catch (Exception e) {
                    log.error("通知地址变更失败: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 通知服务不可用
     */
    private void notifyServiceUnavailable(String serviceName) {
        Set<Runnable> listeners = serviceUnavailableListeners.get(serviceName);
        if (listeners != null) {
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (Exception e) {
                    log.error("通知服务不可用失败: {}", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void close() {
        rwLock.writeLock().lock();
        try {
            addressCache.clear();
            addressChangeListeners.clear();
            serviceUnavailableListeners.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
        log.info("服务地址缓存已清空");
    }
}