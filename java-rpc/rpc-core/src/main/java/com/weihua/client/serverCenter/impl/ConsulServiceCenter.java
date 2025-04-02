package com.weihua.client.serverCenter.impl;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.balance.LoadBalance;
import com.weihua.client.serverCenter.balance.impl.ConsistencyHashBalance;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ConsulServiceCenter implements ServiceCenter {

    private final Consul consulClient;
    private final String consulHost;
    private final int consulPort;
    private final LoadBalance loadBalance;

    // 单例实例
    private static volatile ConsulServiceCenter instance;

    // 将构造函数改为私有
    private ConsulServiceCenter(String host, int port) {
        this.consulHost = host;
        this.consulPort = port;
        // 使用Orbitz的Consul客户端库
        this.consulClient = Consul.builder()
                .withUrl(String.format("http://%s:%d", host, port))
                .withReadTimeoutMillis(20000)
                .withConnectTimeoutMillis(10000)
                .withWriteTimeoutMillis(10000)
                .build();
        // 使用一致性哈希负载均衡
        this.loadBalance = new ConsistencyHashBalance();
    }

    // 获取单例实例的方法
    public static ConsulServiceCenter getInstance(String host, int port) {
        if (instance == null) {
            synchronized (ConsulServiceCenter.class) {
                if (instance == null) {
                    instance = new ConsulServiceCenter(host, port);
                }
            }
        }
        return instance;
    }

    // 提供默认参数的获取实例方法
    public static ConsulServiceCenter getInstance() {
        return getInstance("localhost", 8500);
    }

    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest rpcRequest) {
        String serviceName = rpcRequest.getInterfaceName();
        HealthClient healthClient = consulClient.healthClient();

        try {
            // 查询健康的服务实例
            List<ServiceHealth> serviceHealthList = healthClient.getHealthyServiceInstances(serviceName).getResponse();

            if (serviceHealthList == null || serviceHealthList.isEmpty()) {
                log.warn("未找到服务: {}", serviceName);
                return null;
            }

            // 将ServiceHealth转换为地址字符串列表，用于负载均衡
            List<String> addressList = new ArrayList<>();
            for (ServiceHealth serviceHealth : serviceHealthList) {
                String address = serviceHealth.getService().getAddress();
                int port = serviceHealth.getService().getPort();

                if (address == null || address.isEmpty()) {
                    address = serviceHealth.getNode().getAddress();
                }

                addressList.add(address + ":" + port);
            }

            // 使用一致性哈希负载均衡选择服务实例
            String selectedAddress = loadBalance.balance(addressList);
            log.info("发现服务: {} 地址: {}", serviceName, selectedAddress);
            return parseAddress(selectedAddress);

        } catch (Exception e) {
            log.error("服务发现失败，服务名: {}", serviceName, e);
            return null;
        }
    }

    // 字符串解析为地址
    private InetSocketAddress parseAddress(String address) {
        String[] result = address.split(":");
        return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
    }

    @Override
    public boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature) {
        // 简单实现，暂时不需要重试机制
        return false;
    }

    @Override
    public void close() {
        // Consul客户端资源释放
        try {
            // Orbitz consul客户端不需要显式关闭
            log.info("关闭Consul客户端连接");
        } catch (Exception e) {
            log.error("关闭Consul客户端连接失败", e);
        }
    }
}
