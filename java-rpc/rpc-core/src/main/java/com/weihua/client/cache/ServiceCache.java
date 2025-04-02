package com.weihua.client.cache;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class ServiceCache {
    private static Map<String, List<String>> cache = new HashMap<>();

    /**
     * 将服务实例添加到缓存
     * 
     * @return 如果是新增返回true，如果已存在返回false
     */
    public boolean addServcieToCache(String serviceName, String address) {
        List<String> addresslist = cache.getOrDefault(serviceName, new ArrayList<>());

        // 检查地址是否已存在
        if (addresslist.contains(address)) {
            return false; // 地址已存在，不需要添加
        }

        addresslist.add(address);
        cache.put(serviceName, addresslist);
        log.info("将name为" + serviceName + "和地址为" + address + "的服务添加到本地缓存中");
        return true; // 成功添加新地址
    }

    public void replaceServiceAddress(String serviceName, String oldAddress, String newAddress) {
        List<String> addresslist = cache.getOrDefault(serviceName, null);
        if (addresslist != null) {
            int old_index = addresslist.indexOf(oldAddress);
            if (old_index >= 0) {
                addresslist.set(old_index, newAddress);
            }
        }
    }

    public List<String> getServiceFromCache(String serviceName) {
        return cache.getOrDefault(serviceName, null);
    }

    /**
     * 从缓存中删除服务实例
     * 
     * @return 如果成功删除返回true，如果不存在返回false
     */
    public boolean delete(String serviceName, String address) {
        List<String> addresslist = cache.getOrDefault(serviceName, null);
        if (addresslist != null) {
            boolean removed = addresslist.remove(address);
            cache.put(serviceName, addresslist);
            return removed;
        }
        return false;
    }

    /**
     * 移除指定服务的所有实例
     * 
     * @return 如果服务存在并被移除返回true，否则返回false
     */
    public boolean removeService(String serviceName) {
        List<String> removed = cache.remove(serviceName);
        if (removed != null) {
            log.info("已移除服务{}的所有实例", serviceName);
            return true;
        }
        return false;
    }
}