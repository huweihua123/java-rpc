/*
 * @Author: weihua hu
 * @Date: 2025-03-21 19:20:10
 * @LastEditTime: 2025-04-06 18:11:02
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.serverCenter.balance;

import com.weihua.client.invoker.Invoker;
import common.message.RpcRequest;

import java.util.List;

import common.spi.annotation.SPI;

/**
 * 负载均衡策略接口
 */
@SPI("consistentHash")
public interface LoadBalance {
    /**
     * 基于地址的负载均衡(旧方法，保留兼容性)
     */
    String balance(List<String> addressList);

    /**
     * 基于Invoker的负载均衡
     */
    default Invoker select(List<Invoker> invokers, RpcRequest request) {
        // 默认实现，子类应该重写此方法
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        // 随机选择一个
        return invokers.get((int) (Math.random() * invokers.size()));
    }
}
