package com.weihua.client.serverCenter.balance.impl;

import com.weihua.client.serverCenter.balance.LoadBalance;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConsistencyHashBalance implements LoadBalance {
    private static final int VIRTUAL_NUM = 5;

    private final ConcurrentNavigableMap<Integer, String> shards = new ConcurrentSkipListMap<>();
    private final List<String> realLists = new CopyOnWriteArrayList<>();
    private final Object initLock = new Object();

    public static int getVirtualNum() {
        return VIRTUAL_NUM;
    }

    private static int getHash(String str) {
        int hash = 0x811C9DC5;
        final int FNV_PRIME = 0x01000193;

        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash = (hash * FNV_PRIME) ^ (b & 0xFF);
        }
        return hash;
    }

    public void init(List<String> serviceList) {
        synchronized (initLock) {
            if (!shards.isEmpty()) {
                return;
            }
            for (String service : serviceList) {
                addNode(service);
            }
        }
    }

    public void addNode(String node) {
        if (!realLists.contains(node)) {
            realLists.add(node);
            for (int i = 0; i < VIRTUAL_NUM; i++) {
                String vir_serivce = node + "&&VN" + i;
                int hash = getHash(vir_serivce);
                shards.put(hash, vir_serivce);
            }
        }
    }

    public void delNode(String node) {
        if (realLists.contains(node)) {
            realLists.remove(node);
            for (int i = 0; i < VIRTUAL_NUM; i++) {
                String vir_serivce = node + "&&VN" + i;
                int hash = getHash(vir_serivce);
                shards.remove(hash);
            }
        }
    }

    public String getServer(String node, List<String> serviceList) {
        if (shards.isEmpty()) {
            init(serviceList);
        }

        if (shards.isEmpty()) {
            throw new IllegalStateException("No available servers in the shards map");
        }

        int hash = getHash(node);
        Integer key = null;

        ConcurrentNavigableMap<Integer, String> subMap = shards.tailMap(hash);

        if (subMap.isEmpty()) {
            key = shards.firstKey();
        } else {
            key = subMap.firstKey();
        }
        String virtual_node = shards.get(key);
        return virtual_node.substring(0, virtual_node.indexOf("&&"));
    }

    @Override
    public String balance(List<String> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            throw new IllegalArgumentException("Address list cannot be null or empty");
        }

        String random = UUID.randomUUID().toString();
        return getServer(random, addressList);
    }
}