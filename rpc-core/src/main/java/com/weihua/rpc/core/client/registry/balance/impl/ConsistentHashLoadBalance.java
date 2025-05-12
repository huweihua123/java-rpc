package com.weihua.rpc.core.client.registry.balance.impl;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 一致性哈希负载均衡实现
 */
@Slf4j
public class ConsistentHashLoadBalance extends AbstractLoadBalance {

    // 服务与一致性哈希环的映射
    private final Map<String, ConsistentHashSelector> selectors = new ConcurrentHashMap<>();

    @Override
    protected Invoker doSelect(List<Invoker> invokers, RpcRequest request) {
        String serviceName = request.getInterfaceName();
        String methodName = request.getMethodName();

        // 用于区分不同服务+方法的选择器
        String key = serviceName + "." + methodName;

        // 如果没有可用的服务提供者，清理对应的选择器并返回null
        if (invokers == null || invokers.isEmpty()) {
            // 清理不再需要的选择器
            ConsistentHashSelector removed = selectors.remove(key);
            if (removed != null) {
                log.debug("服务无可用提供者，移除哈希选择器: 服务={}, 方法={}", serviceName, methodName);
            }
            return null;
        }

        // 检查是否需要创建或更新选择器
        ConsistentHashSelector selector = selectors.get(key);
        if (selector == null) {
            // 首次创建选择器
            log.debug("首次创建一致性哈希选择器: 服务={}, 方法={}, 提供者数量={}",
                    serviceName, methodName, invokers.size());
            selectors.put(key, new ConsistentHashSelector(invokers, 160));
            selector = selectors.get(key);
        } else {
            // 检查是否有节点变化，有则进行增量更新
            // 如果所有节点都下线，updateInvokers方法中会清空内部数据
            selector.updateInvokers(invokers);
        }

        // 生成请求的哈希键，用于选择节点
        String requestKey = buildRequestKey(request);

        // 返回选择的Invoker
        return selector.select(requestKey);
    }

    /**
     * 构建请求键
     */
    private String buildRequestKey(RpcRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getInterfaceName())
                .append(".")
                .append(request.getMethodName());

        // 添加参数类型和值，确保相同参数的请求映射到相同的服务
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();

        if (parameters != null && parameters.length > 0) {
            for (int i = 0; i < parameters.length; i++) {
                Object param = parameters[i];
                if (param != null) {
                    sb.append(".")
                            .append(param.toString());
                }
            }
        }

        return sb.toString();
    }

    /**
     * 一致性哈希选择器
     */
    private static class ConsistentHashSelector {
        // 虚拟节点到真实节点的映射
        private final TreeMap<Long, Invoker> virtualNodes = new TreeMap<>();
        // 记录当前的invokers集合，用于检测变化
        private Set<String> invokerAddresses;
        // 每个真实节点对应的虚拟节点数
        private final int virtualNodesNum;

        /**
         * 构造函数
         *
         * @param invokers        调用者列表
         * @param virtualNodesNum 每个调用者的虚拟节点数
         */
        ConsistentHashSelector(List<Invoker> invokers, int virtualNodesNum) {
            this.virtualNodesNum = virtualNodesNum;
            this.invokerAddresses = getInvokerAddresses(invokers);

            // 初始化所有invoker的虚拟节点
            for (Invoker invoker : invokers) {
                addInvokerVirtualNodes(invoker);
            }
        }

        /**
         * 更新invokers列表，只处理变更的节点
         */
        public void updateInvokers(List<Invoker> newInvokers) {

            // 如果新列表为空，清空所有节点并返回
            if (newInvokers == null || newInvokers.isEmpty()) {
                log.debug("所有服务提供者已下线，清空哈希环");
                virtualNodes.clear();
                invokerAddresses = Collections.emptySet();
                return;
            }

            Set<String> newAddresses = getInvokerAddresses(newInvokers);

            // 如果地址集合完全相同，无需更新
            if (newAddresses.equals(invokerAddresses)) {
                return;
            }

            // 找出新增的节点
            Set<String> addedAddresses = new HashSet<>(newAddresses);
            addedAddresses.removeAll(invokerAddresses);

            // 找出移除的节点
            Set<String> removedAddresses = new HashSet<>(invokerAddresses);
            removedAddresses.removeAll(newAddresses);

            // 处理节点变化
            if (!addedAddresses.isEmpty() || !removedAddresses.isEmpty()) {
                log.debug("检测到服务提供者变化: 新增={}, 移除={}", addedAddresses.size(), removedAddresses.size());

                // 添加新节点的虚拟节点
                for (Invoker invoker : newInvokers) {
                    if (addedAddresses.contains(invoker.getAddress().toString())) {
                        addInvokerVirtualNodes(invoker);
                    }
                }

                // 移除已删除节点的虚拟节点
                if (!removedAddresses.isEmpty()) {
                    removeInvokerVirtualNodes(removedAddresses);
                }

                // 更新当前的地址集合
                this.invokerAddresses = newAddresses;
            }
        }

        /**
         * 将invokers列表转换为地址集合
         */
        private Set<String> getInvokerAddresses(List<Invoker> invokers) {
            return invokers.stream()
                    .map(invoker -> invoker.getAddress().toString())
                    .collect(Collectors.toSet());
        }

        /**
         * 为单个invoker添加虚拟节点
         */
        private void addInvokerVirtualNodes(Invoker invoker) {
            String address = invoker.getAddress().toString();

            // 为该invoker创建virtualNodesNum个虚拟节点
            for (int i = 0; i < virtualNodesNum; i++) {
                String nodeKey = address + "#" + i;
                byte[] digest = md5(nodeKey);

                // 一个MD5生成4个虚拟节点
                for (int h = 0; h < 4; h++) {
                    long hash = hash(digest, h);
                    virtualNodes.put(hash, invoker);
                }
            }
        }

        /**
         * 移除指定地址的所有虚拟节点
         */
        private void removeInvokerVirtualNodes(Set<String> addresses) {
            // 遍历所有虚拟节点，移除匹配的节点
            // 使用迭代器安全地移除元素
            Iterator<Map.Entry<Long, Invoker>> it = virtualNodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Invoker> entry = it.next();
                String nodeAddress = entry.getValue().getAddress().toString();
                if (addresses.contains(nodeAddress)) {
                    it.remove();
                }
            }
        }

        /**
         * 根据请求键选择调用者
         */
        public Invoker select(String requestKey) {
            byte[] digest = md5(requestKey);
            // 只使用第一部分的哈希值
            long hash = hash(digest, 0);

            // 找到第一个大于等于hash的节点
            Map.Entry<Long, Invoker> entry = virtualNodes.ceilingEntry(hash);

            // 如果没有找到，则返回第一个节点
            if (entry == null) {
                entry = virtualNodes.firstEntry();
            }

            return entry.getValue();
        }

        /**
         * 计算MD5摘要
         */
        private byte[] md5(String key) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                return md.digest(key.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                log.error("MD5算法不可用", e);
                throw new RuntimeException(e);
            }
        }

        /**
         * 从MD5摘要中提取哈希值
         */
        private long hash(byte[] digest, int idx) {
            return ((long) (digest[3 + idx * 4] & 0xFF) << 24) |
                    ((long) (digest[2 + idx * 4] & 0xFF) << 16) |
                    ((long) (digest[1 + idx * 4] & 0xFF) << 8) |
                    ((long) (digest[idx * 4] & 0xFF)) & 0xFFFFFFFFL;
        }
    }
}