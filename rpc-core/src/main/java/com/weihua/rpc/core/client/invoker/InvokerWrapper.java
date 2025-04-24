package com.weihua.rpc.core.client.invoker;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Invoker包装器，整合连接状态和服务引用信息
 */
public class InvokerWrapper {
    // 实际的调用器
    private volatile Invoker invoker;
    // 服务地址
    private final InetSocketAddress address;
    // 使用此连接的服务集合
    private final Set<String> usingServices;
    // 重试计数
    private volatile int retryCount;
    // 最后一次重试时间
    private volatile long lastRetryTime;
    // 是否已确认下线
    private volatile boolean confirmedDown;
    // 最后活跃时间
    private volatile long lastActiveTime;

    public InvokerWrapper(Invoker invoker, InetSocketAddress address) {
        this.invoker = invoker;
        this.address = address;
        this.retryCount = 0;
        this.lastRetryTime = 0;
        this.confirmedDown = false;
        this.lastActiveTime = System.currentTimeMillis();
        this.usingServices = ConcurrentHashMap.newKeySet();
    }

    public void addUsingService(String serviceName) {
        usingServices.add(serviceName);
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void removeUsingService(String serviceName) {
        usingServices.remove(serviceName);
    }

    public boolean isUsedByAnyService() {
        return !usingServices.isEmpty();
    }

    public boolean isIdle(long idleTimeout) {
        return usingServices.isEmpty() &&
                System.currentTimeMillis() - lastActiveTime > idleTimeout;
    }

    public void resetRetry() {
        this.retryCount = 0;
        this.lastRetryTime = 0;
        this.confirmedDown = false;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void updateLastActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public void setInvoker(Invoker invoker) {
        this.invoker = invoker;
    }

    public boolean isAvailable() {
        return invoker != null && invoker.isAvailable() && !confirmedDown;
    }

    public boolean isConfirmedDown() {
        return confirmedDown;
    }

    public void setConfirmedDown(boolean confirmedDown) {
        this.confirmedDown = confirmedDown;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getLastRetryTime() {
        return lastRetryTime;
    }

    public void setLastRetryTime(long lastRetryTime) {
        this.lastRetryTime = lastRetryTime;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Set<String> getUsingServices() {
        return usingServices;
    }
}