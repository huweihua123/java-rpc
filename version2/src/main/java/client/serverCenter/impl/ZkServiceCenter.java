/*
 * @Author: weihua hu
 * @Date: 2025-03-19 19:02:55
 * @LastEditTime: 2025-03-21 16:48:43
 * @LastEditors: weihua hu
 * @Description: 
 */
package client.serverCenter.impl;

import client.cache.ServiceCache;
import client.serverCenter.ServiceCenter;
import client.serverCenter.watch.WatchZk;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.InetSocketAddress;
import java.util.List;

public class ZkServiceCenter implements ServiceCenter {
    //zookeeper根路径节点
    private static final String ROOT_PATH = "MyRPC";
    // curator 提供的zookeeper客户端
    private CuratorFramework client;
    private ServiceCache cache;

    private WatchZk watcher;

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

        watcher.watchToUpdate(ROOT_PATH);
    }

    //根据服务名（接口名）返回地址
    @Override
    public InetSocketAddress serviceDiscovery(String serviceName) {
        try {
            List<String> strings = client.getChildren().forPath("/" + serviceName);
            // 这里默认用的第一个，后面加负载均衡
            String string = strings.get(0);
            return parseAddress(string);
        } catch (Exception e) {
            e.printStackTrace();
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
}
