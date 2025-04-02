/*
 * @Author: weihua hu
 * @Date: 2025-03-20 20:47:34
 * @LastEditTime: 2025-03-29 16:42:55
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.server.serviceCenter.impl;

import com.weihua.annotation.Retryable;
import com.weihua.server.serviceCenter.ServiceRegister;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ZkServiceRegisterImpl implements ServiceRegister {
    private static final String ROOT_PATH = "MyRPC";
    private static final String RETRY = "CanRetry";
    private CuratorFramework client;

    public ZkServiceRegisterImpl() {
        RetryPolicy policy = new ExponentialBackoffRetry(1000, 3);
        this.client = CuratorFrameworkFactory.builder().connectString("127.0.0.1:2182")
                .sessionTimeoutMs(40000).retryPolicy(policy).namespace(ROOT_PATH).build();
        this.client.start();
    }

//    @Override
//    public void register(String serviceName, InetSocketAddress serviceAddress) {
//        try {
//            // serviceName创建成永久节点，服务提供者下线时，不删服务名，只删地址
//            if (client.checkExists().forPath("/" + serviceName) == null) {
//                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath("/" + serviceName);
//            }
//            // 路径地址，一个/代表一个节点
//            String path = "/" + serviceName + "/" + getServiceAddress(serviceAddress);
//            // 临时节点，服务器下线就删除节点
//            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);
//        } catch (Exception e) {
//            log.error("服务注册失败，服务名：{}，错误信息：{}", serviceName, e.getMessage(), e);
//        }
//
//    }

    @Override
    public void register(Class<?> clazz, InetSocketAddress serviceAddress) {
        String serviceName = clazz.getName();
        try {
            if (client.checkExists().forPath("/" + serviceName) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath("/" + serviceName);
                log.info("服务节点{}创建成功" + serviceName);
            }
            String path = "/" + serviceName + "/" + getServiceAddress(serviceAddress);
            if (client.checkExists().forPath(path) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);
                log.info("服务地址{}注册成功", path);
            } else {
                log.info("服务地址{}已经存在，跳过注册", path);
            }

            // 注册白名单
            List<String> retryableMethods = getRetryableMethod(clazz);
            log.info("可重试的方法：{}", retryableMethods);
            CuratorFramework rootClient = client.usingNamespace(RETRY);
            for (String retryableMethod : retryableMethods) {
                rootClient.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath("/" + getServiceAddress(serviceAddress) + "/" + retryableMethod);
            }

        } catch (Exception e) {
            log.error("服务注册失败，服务名：{}，错误信息：{}", serviceName, e.getMessage(), e);
        }
    }

    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() +
                ":" +
                serverAddress.getPort();
    }

    @Override
    public String toString() {
        return "zookeeper";
    }

    private List<String> getRetryableMethod(Class<?> clazz) {
        List<String> retryableMethods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Retryable.class)) {
                String methodSignature = getMethodSignature(clazz, method);
                retryableMethods.add(methodSignature);
            }
        }

        return retryableMethods;
    }

    private String getMethodSignature(Class<?> clazz, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append("#").append(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            sb.append(parameterTypes[i].getName());
            if (i < parameterTypes.length - 1) {
                sb.append(",");
            } else {
                sb.append(")");
            }
        }
        return sb.toString();
    }
}
