package com.weihua.rpc.core.client.registry.balance;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.invoker.Invoker;
import com.weihua.rpc.core.condition.ConditionalOnClientMode;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡实现
 */
@Slf4j
@Component("consistentHashLoadBalance")
// @ConditionalOnExpression("'${rpc.mode:server}'.equals('client')
// &&'${rpc.loadBalance.type:random}'.equals('consistenthash')")
@ConditionalOnClientMode
@ConditionalOnProperty(name = "rpc.loadBalance.type", havingValue = "consistenthash", matchIfMissing = false)
public class ConsistentHashLoadBalance extends AbstractLoadBalance {

    // 服务与一致性哈希环的映射
    private final Map<String, ConsistentHashSelector> selectors = new ConcurrentHashMap<>();

    @Override
    protected Invoker doSelect(List<Invoker> invokers, RpcRequest request) {
        String serviceName = request.getInterfaceName();
        String methodName = request.getMethodName();

        // 用于区分不同服务+方法+参数类型的选择器
        String key = serviceName + "." + methodName;
        int identityHashCode = System.identityHashCode(invokers);

        // 检查是否需要创建新的选择器
        ConsistentHashSelector selector = selectors.get(key);
        if (selector == null || selector.identityHashCode != identityHashCode) {
            selectors.put(key, new ConsistentHashSelector(invokers, 160, identityHashCode));
            selector = selectors.get(key);
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
        private final int identityHashCode;

        /**
         * 构造函数
         *
         * @param invokers         调用者列表
         * @param virtualNodesNum  每个调用者的虚拟节点数
         * @param identityHashCode 调用者列表的标识哈希码
         */
        ConsistentHashSelector(List<Invoker> invokers, int virtualNodesNum, int identityHashCode) {
            this.identityHashCode = identityHashCode;

            for (Invoker invoker : invokers) {
                // 每个调用者对应多个虚拟节点
                for (int i = 0; i < virtualNodesNum; i++) {
                    // 生成虚拟节点的哈希值
                    String nodeKey = invoker.getAddress().toString() + "#" + i;
                    byte[] digest = md5(nodeKey);

                    // 将16字节的MD5值拆分为4个部分，各自存储为一个虚拟节点
                    for (int h = 0; h < 4; h++) {
                        long hash = hash(digest, h);
                        virtualNodes.put(hash, invoker);
                    }
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
