/*
 * @Author: weihua hu
 * @Date: 2025-04-23 23:59:55
 * @LastEditTime: 2025-04-23 23:59:57
 * @LastEditors: weihua hu
 * @Description: 
 */
/**
 * Invoker包装器，整合连接状态和服务引用信息
 */
public class InvokerWrapper {
    // 实际的调用器
    private final Invoker invoker;
    // 服务地址
    private final InetSocketAddress address;
    // 重试计数
    private volatile int retryCount;
    // 最后一次重试时间
    private volatile long lastRetryTime;
    // 是否已确认下线
    private volatile boolean confirmedDown;
    // 最后活跃时间
    private volatile long lastActiveTime;
    // 使用此连接的服务集合
    private final Set<String> usingServices;

    InvokerWrapper(Invoker invoker, InetSocketAddress address) {
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
        lastActiveTime = System.currentTimeMillis();
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
        retryCount = 0;
        lastRetryTime = 0;
        confirmedDown = false;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public boolean isAvailable() {
        return invoker != null && invoker.isAvailable() && !confirmedDown;
    }
}