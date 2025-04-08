package com.weihua.client.serverCenter.balance.impl;

import com.weihua.client.invoker.Invoker;
import com.weihua.client.serverCenter.balance.LoadBalance;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
public class ConsistencyHashBalance implements LoadBalance {
    private static final int VIRTUAL_NUM = 5;

    // 地址版本的哈希环和实际节点列表
    private final ConcurrentNavigableMap<Integer, String> shards = new ConcurrentSkipListMap<>();
    private final List<String> realLists = new CopyOnWriteArrayList<>();

    // Invoker版本的哈希环和实际节点缓存
    private final Map<String, ConcurrentNavigableMap<Integer, Invoker>> invokerShards = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Invoker>> invokerCache = new ConcurrentHashMap<>();

    private final Object initLock = new Object();

    // 性能指标阈值
    private static final int MAX_ACTIVE_REQUESTS = 100; // 最大活跃请求数
    private static final double MAX_RESPONSE_TIME = 300.0; // 最大响应时间(毫秒)
    private static final double MIN_SUCCESS_RATE = 0.9; // 最低成功率

    public static int getVirtualNum() {
        return VIRTUAL_NUM;
    }

    private static int getHash(String str) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < str.length(); i++)
            hash = (hash ^ str.charAt(i)) * p;
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;

        // 如果算出来的值为负数则取其绝对值
        if (hash < 0)
            hash = Math.abs(hash);
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
        Integer key;

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

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }

        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        // 获取服务名作为哈希环的key
        String serviceName = request.getInterfaceName();

        // 初始化或更新服务的哈希环
        initInvokerShards(serviceName, invokers);

        // 生成请求的哈希值
        // 组合方法名和第一个参数值作为哈希key，保证相同调用映射到相同节点
        String methodName = request.getMethodName();
        Object[] params = request.getParameters();
        String hashKey = methodName;
        if (params != null && params.length > 0 && params[0] != null) {
            hashKey = methodName + ":" + params[0].toString();
        }

        // 查找对应的Invoker
        int hash = getHash(hashKey);
        ConcurrentNavigableMap<Integer, Invoker> serviceShards = invokerShards.get(serviceName);

        if (serviceShards == null || serviceShards.isEmpty()) {
            // 哈希环为空，随机选择
            int randomIndex = new Random().nextInt(invokers.size());
            return invokers.get(randomIndex);
        }

        // 在哈希环上查找
        ConcurrentNavigableMap<Integer, Invoker> tailMap = serviceShards.tailMap(hash);
        Integer nearestKey = tailMap.isEmpty() ? serviceShards.firstKey() : tailMap.firstKey();
        Invoker selectedInvoker = serviceShards.get(nearestKey);

        // 检查选中的Invoker性能是否异常
        if (isInvokerOverloaded(selectedInvoker)) {
            log.warn("一致性哈希选中的Invoker {} 性能异常(活跃请求数: {}, 响应时间: {}ms, 成功率: {}%), 尝试寻找备选节点",
                    selectedInvoker.getId(),
                    selectedInvoker.getActiveCount(),
                    String.format("%.2f", selectedInvoker.getAvgResponseTime()),
                    String.format("%.2f", selectedInvoker.getSuccessRate() * 100));

            // 尝试寻找附近的健康节点
            Invoker alternativeInvoker = findHealthyAlternative(serviceShards, nearestKey);
            if (alternativeInvoker != null) {
                log.info("一致性哈希负载均衡使用备选Invoker: {}, 请求哈希: {}, 地址: {}",
                        alternativeInvoker.getId(), hash, alternativeInvoker.getAddress());
                return alternativeInvoker;
            }
        }

        log.info("一致性哈希负载均衡选择Invoker: {}, 请求哈希: {}, 地址: {}, 活跃请求数: {}, 响应时间: {}ms",
                selectedInvoker.getId(), hash, selectedInvoker.getAddress(),
                selectedInvoker.getActiveCount(),
                String.format("%.2f", selectedInvoker.getAvgResponseTime()));

        return selectedInvoker;
    }

    /**
     * 判断Invoker是否超载或性能异常
     */
    private boolean isInvokerOverloaded(Invoker invoker) {
        // 检查三个主要性能指标
        return invoker.getActiveCount() > MAX_ACTIVE_REQUESTS ||
                (invoker.getAvgResponseTime() > MAX_RESPONSE_TIME &&
                        invoker.getSuccessRate() < MIN_SUCCESS_RATE);
    }

    /**
     * 寻找哈希环上附近的健康节点
     */
    private Invoker findHealthyAlternative(ConcurrentNavigableMap<Integer, Invoker> serviceShards,
            Integer currentKey) {
        // 向后查找
        ConcurrentNavigableMap<Integer, Invoker> tailMap = serviceShards.tailMap(currentKey, false);
        for (Invoker invoker : tailMap.values()) {
            if (!isInvokerOverloaded(invoker)) {
                return invoker;
            }
        }

        // 向前查找
        ConcurrentNavigableMap<Integer, Invoker> headMap = serviceShards.headMap(currentKey);
        for (Invoker invoker : headMap.values()) {
            if (!isInvokerOverloaded(invoker)) {
                return invoker;
            }
        }

        // 没有找到健康节点，返回null
        return null;
    }

    /**
     * 初始化或更新服务的哈希环
     */
    private void initInvokerShards(String serviceName, List<Invoker> invokers) {
        // 获取当前已缓存的Invoker映射
        Map<String, Invoker> currentCache = invokerCache.computeIfAbsent(
                serviceName, k -> new ConcurrentHashMap<>());

        // 检查是否需要更新哈希环
        boolean needUpdate = false;

        // 检查Invoker列表是否有变化
        for (Invoker invoker : invokers) {
            String address = invoker.getAddress().toString();
            if (!currentCache.containsKey(address)) {
                needUpdate = true;
                break;
            }
        }

        // 现有缓存中的地址数量与当前提供的不一致，也需要更新
        if (currentCache.size() != invokers.size()) {
            needUpdate = true;
        }

        if (needUpdate) {
            synchronized (invokerCache) {
                // 创建新的哈希环
                ConcurrentNavigableMap<Integer, Invoker> newShards = new ConcurrentSkipListMap<>();
                Map<String, Invoker> newCache = new ConcurrentHashMap<>();

                // 将所有invoker添加到哈希环
                for (Invoker invoker : invokers) {
                    String address = invoker.getAddress().toString();
                    newCache.put(address, invoker);

                    // 为每个invoker创建虚拟节点
                    for (int i = 0; i < VIRTUAL_NUM; i++) {
                        String virtualNode = address + "&&VN" + i;
                        int hash = getHash(virtualNode);
                        newShards.put(hash, invoker);
                    }
                }

                // 更新缓存和哈希环
                invokerCache.put(serviceName, newCache);
                invokerShards.put(serviceName, newShards);

                log.debug("更新服务[{}]的一致性哈希环，节点数: {}", serviceName, invokers.size());
            }
        }
    }
}