/*
 * @Author: weihua hu
 * @Date: 2025-03-19 19:02:55
 * @LastEditTime: 2025-03-21 16:48:43
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.client.serverCenter.impl;


import com.weihua.client.cache.ServiceCache;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.balance.LoadBalance;
import com.weihua.client.serverCenter.balance.impl.ConsistencyHashBalance;
import com.weihua.client.serverCenter.watch.WatchZk;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Log4j2
public class ZkServiceCenter implements ServiceCenter {
    //zookeeper根路径节点
    private static final String ROOT_PATH = "MyRPC";

    private static final String RETRY = "CanRetry";
    // curator 提供的zookeeper客户端
    private CuratorFramework client;
    private ServiceCache cache;

    private WatchZk watcher;

    private LoadBalance loadBalance;

    private Set<String> retryServiceCache = new CopyOnWriteArraySet<>();

    //负责zookeeper客户端的初始化，并与zookeeper服务端进行连接
    public ZkServiceCenter() {
        // 指数时间重试
        RetryPolicy policy = new ExponentialBackoffRetry(1000, 3);
        // zookeeper的地址固定，不管是服务提供者还是，消费者都要与之建立连接
        // sessionTimeoutMs 与 zoo.cfg中的tickTime 有关系，
        // zk还会根据minSessionTimeout与maxSessionTimeout两个参数重新调整最后的超时值。默认分别为tickTime 的2倍和20倍
        // 使用心跳监听状态
        this.client = CuratorFrameworkFactory.builder().connectString("127.0.0.1:2182")
                .sessionTimeoutMs(40000).retryPolicy(policy).namespace(ROOT_PATH).build();
        this.client.start();

        System.out.println("zookeeper 连接成功");

        this.cache = new ServiceCache();

        this.watcher = new WatchZk(client, cache);

//        this.loadBalance = new RoundLoadBalance();
        this.loadBalance = new ConsistencyHashBalance();

        watcher.watchToUpdate(ROOT_PATH);
    }

    //    @Override
//    public boolean checkRetry(String serviceName) {
//        boolean canRetry = false;
//
//        try {
//            List<String> serviceNames = client.getChildren().forPath("/" + RETRY);
//            if (serviceNames.contains(serviceName)) {
//                log.info();
//                System.out.println("服务" + serviceName + "在白名单上，可进行重试");
//                return true;
//            }
//        } catch (Exception e) {
////            e.printStackTrace();
//        }
//
//        return canRetry;
//    }
    @Override
    public boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature) {
        if (retryServiceCache.isEmpty()) {
            try {
                CuratorFramework rootClient = client.usingNamespace(RETRY);
                List<String> retryableMethods = rootClient.getChildren().forPath("/" + getServiceAddress(serviceAddress));
                retryServiceCache.addAll(retryableMethods);
            } catch (Exception e) {
                log.info("检查重试失败，方法签名：{}", methodSignature, e);
            }
        }
        return retryServiceCache.contains(methodSignature);
    }

    //根据服务名（接口名）返回地址
//    @Override
//    public InetSocketAddress serviceDiscovery(String serviceName) {
//        /*
//            1、从本地缓存中那地址列表
//            2、本地拿不到在去zookeeper server 去拿
//            3、然后负载均衡算法做处理
//            4、解析string address
//         */
//
//        List<String> addressList = cache.getServcieFromCache(serviceName);
//
//        try {
//            if (addressList == null) {
//                addressList = client.getChildren().forPath("/" + serviceName);
//            }
//            String address = loadBalance.balance(addressList);
//            return parseAddress(address);
//        } catch (Exception e) {
//            log.error("服务发现失败，服务名：{}", serviceName, e);
//        }
//        return null;
//    }

    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest rpcRequest) {
        /*
            1、从本地缓存中那地址列表
            2、本地拿不到在去zookeeper server 去拿
            3、然后负载均衡算法做处理
            4、解析string address
         */

        String serviceName = rpcRequest.getInterfaceName();

        List<String> addressList = cache.getServiceFromCache(serviceName);

        try {
            if (addressList == null) {
                addressList = client.getChildren().forPath("/" + serviceName);

                List<String> cachedAddresses = cache.getServiceFromCache(serviceName);

                if (cachedAddresses == null || cachedAddresses.isEmpty()) {
                    for (String address : addressList) {
                        cache.addServcieToCache(serviceName, address);
                    }
                }
            }

            if (addressList == null) {
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

    // 地址 -> XXX.XXX.XXX.XXX:port 字符串
    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() +
                ":" +
                serverAddress.getPort();
    }

    // 字符串解析为地址
    private InetSocketAddress parseAddress(String address) {
        String[] result = address.split(":");
        return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
    }

    @Override
    public void close() {
        client.close();
    }
}
