package com.weihua.client.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceCache {
    private static Map<String, List<String>> cache = new HashMap<>();

    public void addServcieToCache(String serviceName, String address) {
        List<String> addresslist = cache.getOrDefault(serviceName, new ArrayList<>());
        addresslist.add(address);
        cache.put(serviceName, addresslist);
        System.out.println("将name为" + serviceName + "和地址为" + address + "的服务添加到本地缓存中");
    }

    public void replaceServiceAddress(String serviceName, String oldAddress, String newAddress) {
        List<String> addresslist = cache.getOrDefault(serviceName, null);
        if (addresslist != null) {
            int old_index = addresslist.indexOf(oldAddress);
            addresslist.set(old_index, newAddress);
        }
    }

    public List<String> getServcieFromCache(String serviceName) {
        return cache.getOrDefault(serviceName, null);
    }

    public void delete(String serviceName, String address) {
        List<String> addresslist = cache.getOrDefault(serviceName, null);
        if (addresslist != null) {
            addresslist.remove(address);
            cache.put(serviceName, addresslist);
        }
    }
}
