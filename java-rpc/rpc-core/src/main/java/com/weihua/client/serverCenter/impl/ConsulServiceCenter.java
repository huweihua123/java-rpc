package com.weihua.client.serverCenter.impl;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.weihua.client.cache.ServiceCache;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.balance.LoadBalance;
import com.weihua.client.serverCenter.balance.impl.ConsistencyHashBalance;
import com.weihua.client.serverCenter.watch.WatchConsul;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Log4j2
public class ConsulServiceCenter implements ServiceCenter {
    private Consul consulClient;
    private HealthClient healthClient;
    private ServiceCache cache;
    private LoadBalance loadBalance;
    private WatchConsul watcher;
    private Set<String> retryServiceCache = new CopyOnWriteArraySet<>();

    public ConsulServiceCenter() {
        // 初始化Consul客户端
        this.consulClient = Consul.builder().withUrl("http://localhost:8500").build();
        this.healthClient = consulClient.healthClient();

        System.out.println("Consul 连接成功");

        this.cache = new ServiceCache();
        this.loadBalance = new ConsistencyHashBalance();

        // 初始化监听器
        this.watcher = new WatchConsul(consulClient, cache);
        watcher.startWatch();
    }

    @Override
    public boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature) {
        if (retryServiceCache.isEmpty()) {
            try {
                // 从Consul获取服务元数据
                String serviceId = getServiceAddress(serviceAddress);
                List<ServiceHealth> services = healthClient.getAllInstances(serviceId.split(":")[0]).getResponse();

                for (ServiceHealth serviceHealth : services) {
                    if ((serviceHealth.getService().getAddress() + ":" + serviceHealth.getService().getPort())
                            .equals(serviceId)) {
                        // 获取服务的元数据
                        Map<String, String> meta = serviceHealth.getService().getMeta();
                        // 遍历元数据查找可重试方法
                        for (Map.Entry<String, String> entry : meta.entrySet()) {
                            if (entry.getKey().startsWith("retryable-")) {
                                retryServiceCache.add(entry.getValue());
                            }
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.info("检查重试失败，方法签名：{}", methodSignature, e);
            }
        }

        return retryServiceCache.contains(methodSignature);
    }

    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest rpcRequest) {
        /*
         * 1、从本地缓存中获取地址列表
         * 2、本地拿不到再去Consul获取
         * 3、然后负载均衡算法做处理
         * 4、解析string address
         */

        String serviceName = rpcRequest.getInterfaceName();
        List<String> addressList = cache.getServiceFromCache(serviceName);

        try {
            if (addressList == null || addressList.isEmpty()) {
                // 从Consul获取健康的服务实例
                List<ServiceHealth> services = healthClient.getHealthyServiceInstances(serviceName).getResponse();
                addressList = new ArrayList<>();

                for (ServiceHealth serviceHealth : services) {
                    String address = serviceHealth.getService().getAddress() + ":"
                            + serviceHealth.getService().getPort();
                    addressList.add(address);
                    cache.addServcieToCache(serviceName, address);
                }
            }

            if (addressList == null || addressList.isEmpty()) {
                log.warn("未找到服务：{}", serviceName);
                return null;
            }

            String address = loadBalance.balance(addressList);
            return parseAddress(address);
        } catch (Exception e) {
            log.error("服务发现失败，服务名：{}", serviceName, e);
        }

        return null;
    }

    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() + ":" + serverAddress.getPort();
    }

    private InetSocketAddress parseAddress(String address) {
        String[] result = address.split(":");
        return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
    }

    @Override
    public void close() {
        watcher.stopWatch();
    }

    @Override
    public String toString() {
        return "consul";
    }
}
/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-02 13:27:20
 * 
 * @LastEditTime: 2025-04-02 13:27:20
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
