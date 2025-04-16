package com.weihua.rpc.core.client.pool;

import com.weihua.rpc.core.client.cache.ServiceAddressCache;
import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.invoker.ChannelInvoker;
import com.weihua.rpc.core.client.invoker.Invoker;
import com.weihua.rpc.core.client.netty.NettyRpcClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Invoker管理器
 * 管理所有服务连接，支持不同连接策略
 */
@Slf4j
@Component
public class InvokerManager {

    // 按地址存储Invoker（每个地址一个固定Invoker）
    private final Map<InetSocketAddress, Invoker> invokerMap = new ConcurrentHashMap<>();
    // 按地址存储服务下线状态
    private final Map<InetSocketAddress, ServiceStatus> serviceStatusMap = new ConcurrentHashMap<>();
    // 按服务名存储关联的地址列表
    private final Map<String, Set<InetSocketAddress>> serviceAddressMap = new ConcurrentHashMap<>();
    // 新增方法：跟踪已订阅的服务
    private final Set<String> subscribedServices = ConcurrentHashMap.newKeySet();
    // 连接创建锁
    private final ReentrantLock lock = new ReentrantLock();
    @Autowired
    private ClientConfig clientConfig;
    @Autowired
    @Lazy
    private NettyRpcClient nettyRpcClient;
    @Autowired
    private ServiceAddressCache addressCache;
    // 连接健康检查调度器
    private ScheduledExecutorService scheduler;

    // 是否已启动
    private volatile boolean started = false;

    // 基础重连间隔（毫秒），将作为指数退避的初始值
    private int reconnectInterval;

    // 最大重试次数
    private int maxRetryAttempts;

    // 连接策略
    private ConnectionMode connectionMode;

    // 指数退避乘数
    private double backoffMultiplier;

    // 最大退避时间（毫秒）
    private int maxBackoffTime;

    // 是否添加随机抖动
    private boolean addJitter;

    // 最小重试间隔（毫秒）
    private int minRetryInterval;

    /**
     * 将字符串地址转换为InetSocketAddress
     */
    public static InetSocketAddress parseAddress(String address) throws UnknownHostException {
        String[] parts = address.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("地址格式不正确: " + address);
        }

        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        // 解析主机名为IP地址
        InetAddress inetAddress = InetAddress.getByName(host);
        return new InetSocketAddress(inetAddress, port);
    }

    /**
     * 计算下一次重试的间隔时间（指数退避）
     *
     * @param retryCount 当前重试次数
     * @return 下一次重试的间隔时间（毫秒）
     */
    private int calculateBackoffTime(int retryCount) {
        // 基础重试间隔 * (退避乘数 ^ 重试次数)
        double expBackoff = reconnectInterval * Math.pow(backoffMultiplier, retryCount);

        // 确保不小于最小重试间隔
        expBackoff = Math.max(expBackoff, minRetryInterval);

        // 限制最大退避时间
        int nextInterval = (int) Math.min(expBackoff, maxBackoffTime);

        // 添加随机抖动（0-20%之间的随机值）
        if (addJitter) {
            double jitter = 0.2 * nextInterval * ThreadLocalRandom.current().nextDouble();
            nextInterval = (int) (nextInterval + jitter);
        }

        return nextInterval;
    }

    /**
     * 初始化方法，在Bean创建后自动调用
     */
    @PostConstruct
    public void init() {
        // 从配置中加载基础参数
        this.reconnectInterval = clientConfig.getRetryIntervalMillis();
        this.maxRetryAttempts = clientConfig.getMaxRetryAttempts();
        this.connectionMode = clientConfig.getConnectionMode();

        // 设置指数退避策略的参数
        this.backoffMultiplier = clientConfig.getBackoffMultiplier();
        this.maxBackoffTime = clientConfig.getMaxBackoffTime();
        this.addJitter = clientConfig.isAddJitter();
        this.minRetryInterval = clientConfig.getMinRetryInterval();

        // 创建调度器
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "invoker-health-check");
            t.setDaemon(true);
            return t;
        });

        // 启动管理器
        start();

        // 订阅服务地址变更
        // subscribeServiceUpdates();

        log.info("InvokerManager初始化完成，连接模式：{}, 重试策略: 指数退避(初始间隔={}ms, 乘数={}, 最大间隔={}ms, 随机抖动={})",
                connectionMode, reconnectInterval, backoffMultiplier, maxBackoffTime, addJitter ? "启用" : "禁用");
    }

    /**
     * 订阅所有缓存中的服务地址变更
     */
    private void subscribeServiceUpdates() {
        // 对每个服务注册监听器
        addressCache.getAllServiceNames().forEach(this::subscribeServiceAddressChange);
    }

    /**
     * 订阅指定服务的地址变更
     */
    public void subscribeServiceAddressChange(String serviceName) {
        addressCache.subscribeAddressChange(serviceName, addresses -> {
            try {
                updateServiceAddresses(serviceName, addresses);
            } catch (Exception e) {
                log.error("处理服务[{}]地址变更时出错: {}", serviceName, e.getMessage(), e);
            }
        });

        // 添加服务不可用监听器
        addressCache.addServiceUnavailableListener(serviceName, () -> {
            try {
                handleServiceUnavailable(serviceName);
            } catch (Exception e) {
                log.error("处理服务[{}]不可用事件时出错: {}", serviceName, e.getMessage(), e);
            }
        });
    }

    /**
     * 更新服务地址列表
     */
    public void updateServiceAddresses(String serviceName, List<String> addressStrings) {
        if (serviceName == null || addressStrings == null) {
            return;
        }

        log.info("更新服务[{}]地址列表: {}", serviceName, addressStrings);

        try {
            // 转换地址格式
            List<InetSocketAddress> addresses = new ArrayList<>();
            for (String addr : addressStrings) {
                addresses.add(parseAddress(addr));
            }

            // 记录服务的地址列表
            Set<InetSocketAddress> addressSet = serviceAddressMap.computeIfAbsent(
                    serviceName, k -> ConcurrentHashMap.newKeySet());

            // 找出已移除的地址
            Set<InetSocketAddress> removedAddresses = new HashSet<>(addressSet);
            removedAddresses.removeAll(addresses);

            // 找出新增的地址
            Set<InetSocketAddress> newAddresses = new HashSet<>(addresses);
            newAddresses.removeAll(addressSet);

            // 移除不再使用的地址
            for (InetSocketAddress address : removedAddresses) {
                addressSet.remove(address);

                // 检查是否还有其他服务使用此地址
                boolean stillInUse = false;
                for (Set<InetSocketAddress> addrSet : serviceAddressMap.values()) {
                    if (addrSet.contains(address)) {
                        stillInUse = true;
                        break;
                    }
                }

                // 如果没有其他服务使用此地址，则移除连接
                if (!stillInUse) {
                    removeInvoker(address);
                    serviceStatusMap.remove(address);
                }
            }

            // 处理新增地址
            for (InetSocketAddress address : newAddresses) {
                addressSet.add(address);

                // 重置服务状态
                serviceStatusMap.put(address, new ServiceStatus(address));

                // 如果是EAGER模式，则立即创建连接
                if (connectionMode == ConnectionMode.EAGER) {
                    try {
                        log.info("通过eager模式");
                        createOrGetInvoker(address);
                    } catch (Exception e) {
                        log.warn("预连接服务[{}]地址[{}:{}]失败: {}",
                                serviceName, address.getHostString(), address.getPort(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("更新服务[{}]地址列表时出错: {}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * 处理服务不可用事件
     */
    private void handleServiceUnavailable(String serviceName) {
        log.info("处理服务[{}]不可用事件", serviceName);

        // 获取该服务的所有地址
        Set<InetSocketAddress> addresses = serviceAddressMap.get(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        // 将所有地址标记为已确认下线
        for (InetSocketAddress address : addresses) {
            ServiceStatus status = serviceStatusMap.get(address);
            if (status != null) {
                status.confirmedDown = true;
                log.info("标记服务地址[{}:{}]为已确认下线状态",
                        address.getHostString(), address.getPort());
            }
        }
    }

    /**
     * 启动管理器，初始化连接健康检查任务
     */
    public void start() {
        if (!started) {
            synchronized (this) {
                if (!started) {
                    // 启动连接健康检查任务
                    scheduler.scheduleAtFixedRate(
                            this::healthCheckTask,
                            5, 10, TimeUnit.SECONDS);
                    started = true;
                    log.info("InvokerManager已启动，连接健康检查任务已开始");
                }
            }
        }
    }

    /**
     * 连接健康检查任务
     */
    private void healthCheckTask() {
        try {
            // log.info("执行连接健康检查任务");
            for (Map.Entry<InetSocketAddress, Invoker> entry : invokerMap.entrySet()) {
                InetSocketAddress address = entry.getKey();
                Invoker invoker = entry.getValue();
                ServiceStatus status = serviceStatusMap.get(address);

                // 如果连接不可用且未确认下线，尝试重连
                if (!invoker.isAvailable() && (status == null || !status.confirmedDown)) {
                    // 获取或创建状态对象
                    if (status == null) {
                        status = new ServiceStatus(address);
                        serviceStatusMap.put(address, status);
                    }

                    // 检查是否超过最大重试次数
                    if (status.retryCount >= maxRetryAttempts) {
                        log.warn("连接[{}:{}]已达到最大重试次数({}次)，不再重试",
                                address.getHostString(), address.getPort(), maxRetryAttempts);
                        continue;
                    }

                    // 计算当前退避时间
                    int backoffTime = calculateBackoffTime(status.retryCount);

                    // 检查是否需要重试（按指数退避策略检查）
                    long now = System.currentTimeMillis();
                    long elapsed = now - status.lastRetryTime;
                    if (status.lastRetryTime > 0 && elapsed < backoffTime) {
                        continue;
                    }

                    log.info("检测到连接不可用，准备重新连接(第{}次尝试): {}:{}, 退避时间: {}ms",
                            status.retryCount + 1, address.getHostString(), address.getPort(), backoffTime);

                    try {
                        // 销毁旧连接
                        invoker.destroy();

                        // 更新状态
                        status.retryCount++;
                        status.lastRetryTime = now;

                        // 创建新连接（异步）
                        createNewConnection(address);
                    } catch (Exception e) {
                        log.warn("重新连接失败: {}:{}, 错误: {}",
                                address.getHostString(), address.getPort(), e.getMessage());
                    }
                }
            }
            printState();

            // 打印状态
            if (log.isDebugEnabled()) {
                printState();
            }
        } catch (Exception e) {
            log.error("执行连接健康检查任务时出错", e);
        }
    }

    /**
     * 创建新连接
     */

    private void createNewConnection(InetSocketAddress address) {
        // 获取Bootstrap
        Bootstrap bootstrap = nettyRpcClient.getBootstrap();
        if (bootstrap == null) {
            log.error("Bootstrap未设置，无法创建连接");
            return;
        }

        ServiceStatus status = serviceStatusMap.get(address);
        if (status == null) {
            status = new ServiceStatus(address);
            serviceStatusMap.put(address, status);
        }

        // 如果服务已确认下线，不再重连
        if (status.confirmedDown) {
            log.info("服务地址[{}:{}]已确认下线，不再尝试连接",
                    address.getHostString(), address.getPort());
            return;
        }

        // 为lambda表达式创建不可变引用
        final ServiceStatus finalStatus = status;
        final InetSocketAddress finalAddress = address;

        // 异步连接创建
        bootstrap.connect(finalAddress).addListener(future -> {
            if (future.isSuccess()) {
                try {
                    // 使用ChannelFuture的channel()方法替代getNow()
                    Channel channel = ((io.netty.channel.ChannelFuture) future).channel();

                    // 确保channel不为空并且已激活
                    if (channel != null && channel.isActive()) {
                        Invoker newInvoker = new ChannelInvoker(channel, clientConfig.getRequestTimeout());

                        // 替换旧连接
                        invokerMap.put(finalAddress, newInvoker);

                        // 重置重试状态
                        if (finalStatus != null) {
                            finalStatus.retryCount = 0;
                            finalStatus.lastRetryTime = 0;
                            finalStatus.confirmedDown = false;
                        }

                        log.info("成功重新连接到服务: {}:{}",
                                finalAddress.getHostString(), finalAddress.getPort());
                    } else {
                        // 虽然future成功但channel无效，当作失败处理
                        handleConnectionFailure(finalStatus, finalAddress);
                        log.warn("连接异常: {}:{}, Channel无效或未激活",
                                finalAddress.getHostString(), finalAddress.getPort());
                    }
                } catch (Exception e) {
                    // 处理创建Invoker过程中的异常
                    handleConnectionFailure(finalStatus, finalAddress);
                    log.warn("创建Invoker失败: {}:{}, 错误: {}",
                            finalAddress.getHostString(), finalAddress.getPort(), e.getMessage());
                }
            } else {
                handleConnectionFailure(finalStatus, finalAddress);
            }
        });
    }

    /**
     * 处理连接失败情况
     */
    private void handleConnectionFailure(ServiceStatus status, InetSocketAddress address) {
        if (status != null) {
            status.retryCount++;
            status.lastRetryTime = System.currentTimeMillis();

            // 检查是否已达到最大重试次数
            if (status.retryCount >= maxRetryAttempts) {
                status.confirmedDown = true; // 标记为已确认下线
                log.warn("连接[{}:{}]已达到最大重试次数({})，标记为已确认下线",
                        address.getHostString(), address.getPort(), maxRetryAttempts);

                // 新增：清理资源
                cleanupDownedService(address);
                return; // 不再重试
            }

            // 计算下一次重试的时间间隔（指数退避）
            int backoffTime = calculateBackoffTime(status.retryCount);

            log.warn("连接失败: {}:{}, 将在{}ms后重试, 已尝试{}次/最大{}次",
                    address.getHostString(), address.getPort(),
                    backoffTime, status.retryCount, maxRetryAttempts);

            // 延迟使用计算出的退避时间
            scheduler.schedule(() -> createNewConnection(address),
                    backoffTime, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 清理已确认下线的服务资源
     */
    private void cleanupDownedService(InetSocketAddress address) {
        try {
            // 1. 从所有服务的地址列表中移除
            for (Map.Entry<String, Set<InetSocketAddress>> entry : serviceAddressMap.entrySet()) {
                String serviceName = entry.getKey();
                Set<InetSocketAddress> addresses = entry.getValue();

                if (addresses.remove(address)) {
                    log.info("从服务[{}]的地址列表中移除已下线地址: {}:{}",
                            serviceName, address.getHostString(), address.getPort());
                }
            }

            // 2. 从invokerMap中移除并销毁Invoker
            Invoker invoker = invokerMap.remove(address);
            if (invoker != null) {
                try {
                    invoker.destroy();
                    log.info("移除并销毁已下线的Invoker: {}:{}",
                            address.getHostString(), address.getPort());
                } catch (Exception e) {
                    log.error("销毁已下线的Invoker时发生错误: {}:{} - {}",
                            address.getHostString(), address.getPort(), e.getMessage());
                }
            }

            // 3. 通知地址缓存该地址已下线（可选）
            // 如果addressCache提供了相关方法
        } catch (Exception e) {
            log.error("清理已下线服务资源时出错: {}:{} - {}",
                    address.getHostString(), address.getPort(), e.getMessage());
        }
    }

    /**
     * 获取或创建Invoker (InetSocketAddress版)
     */
    public Invoker getInvoker(InetSocketAddress socketAddress) throws Exception {
        return createOrGetInvoker(socketAddress);
    }

    /**
     * 创建或获取Invoker（内部方法）
     */
    private Invoker createOrGetInvoker(InetSocketAddress socketAddress) throws Exception {
        // 先检查是否已有可用连接
        Invoker invoker = invokerMap.get(socketAddress);
        if (invoker != null && invoker.isAvailable()) {
            return invoker;
        }

        // 检查服务是否已确认下线
        ServiceStatus status = serviceStatusMap.get(socketAddress);
        if (status != null && status.confirmedDown) {
            throw new IllegalStateException("服务地址[" + socketAddress.getHostString() +
                    ":" + socketAddress.getPort() + "]已确认下线，无法创建连接");
        }

        // 需要创建新连接
        lock.lock();
        try {
            // 双重检查
            invoker = invokerMap.get(socketAddress);
            if (invoker != null && invoker.isAvailable()) {
                return invoker;
            }

            // 创建新连接
            Bootstrap bootstrap = nettyRpcClient.getBootstrap();
            if (bootstrap == null) {
                throw new IllegalStateException("Bootstrap未设置，无法创建连接");
            }

            log.info("创建新连接: {}:{}", socketAddress.getHostString(), socketAddress.getPort());
            Channel channel = bootstrap.connect(socketAddress).sync().channel();

            if (channel != null && channel.isActive()) {
                Invoker newInvoker = new ChannelInvoker(channel, clientConfig.getRequestTimeout());

                // 替换旧连接（如果有）
                Invoker oldInvoker = invokerMap.put(socketAddress, newInvoker);
                if (oldInvoker != null) {
                    oldInvoker.destroy();
                }

                // 重置服务状态
                if (status != null) {
                    status.retryCount = 0;
                    status.lastRetryTime = 0;
                    status.confirmedDown = false;
                }

                return newInvoker;
            }

            throw new Exception("创建连接失败");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取或创建Invoker (String地址版)
     */
    public Invoker getInvoker(String address) throws Exception {
        return getInvoker(parseAddress(address));
    }

    /**
     * 根据服务名获取所有可用的Invoker
     *
     * @param serviceName 服务名称
     * @return 所有可用的Invoker列表
     */
    public List<Invoker> getInvokers(String serviceName) {
        List<Invoker> result = new ArrayList<>();

        // 获取服务的地址列表
        Set<InetSocketAddress> addresses = serviceAddressMap.get(serviceName);
        log.info("获取服务[{}]的Invoker列表，地址数量: {}", serviceName, addresses != null ? addresses.size() : 0);
        if (addresses == null || addresses.isEmpty()) {
            // 如果本地没有，尝试从缓存获取
            List<String> addressStrings = addressCache.getAddresses(serviceName);
            if (addressStrings != null && !addressStrings.isEmpty()) {
                // 更新地址列表并递归调用
                updateServiceAddresses(serviceName, addressStrings);
                return getInvokers(serviceName);
            }
            return result;
        }

        // 根据连接模式处理
        for (InetSocketAddress address : addresses) {
            try {
                // 检查服务状态
                ServiceStatus status = serviceStatusMap.get(address);
                if (status != null && status.confirmedDown) {
                    continue; // 跳过已确认下线的服务
                }
                // log.info("connectMode: {}", connectionMode);
                // 获取或创建Invoker
                if (connectionMode == ConnectionMode.EAGER) {
                    // log.info("使用EAGER模式获取Invoker: {}:{}", address.getHostString(),
                    // address.getPort());
                    // 对于EAGER模式，直接从map中获取
                    Invoker invoker = invokerMap.get(address);
                    if (invoker != null && invoker.isAvailable()) {
                        result.add(invoker);
                    }
                } else {
                    log.info("使用LAZY模式获取Invoker: {}:{}", address.getHostString(), address.getPort());
                    // 对于LAZY模式，按需创建连接
                    Invoker invoker = createOrGetInvoker(address);
                    if (invoker != null && invoker.isAvailable()) {
                        result.add(invoker);
                    }
                }
            } catch (Exception e) {
                log.warn("为服务[{}]获取地址[{}:{}]的Invoker失败: {}",
                        serviceName, address.getHostString(), address.getPort(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * 移除并销毁指定地址的Invoker
     */
    public void removeInvoker(InetSocketAddress address) {
        Invoker invoker = invokerMap.remove(address);
        if (invoker != null) {
            try {
                invoker.destroy();
                log.info("移除并销毁Invoker: {}:{}",
                        address.getHostString(), address.getPort());
            } catch (Exception e) {
                log.error("销毁Invoker失败: {}:{} - {}",
                        address.getHostString(), address.getPort(), e.getMessage());
            }
        }
    }

    /**
     * 打印状态信息
     */
    public void printState() {
        for (Map.Entry<InetSocketAddress, Invoker> entry : invokerMap.entrySet()) {
            InetSocketAddress address = entry.getKey();
            Invoker invoker = entry.getValue();
            ServiceStatus status = serviceStatusMap.get(address);

            if (invoker != null) {
                log.info("连接状态 - 地址: {}:{}, 可用: {}, 活跃请求: {}, 总请求数: {}, 平均响应时间: {}ms, 成功率: {}%, 重试次数: {}{}",
                        address.getHostString(), address.getPort(),
                        invoker.isAvailable(), invoker.getActiveCount(),
                        invoker.getRequestCount(),
                        String.format("%.2f", invoker.getAvgResponseTime()),
                        String.format("%.2f", invoker.getSuccessRate() * 100),
                        status != null ? status.retryCount : 0,
                        status != null && status.confirmedDown ? ", 已确认下线" : "");
            }
        }
    }

    /**
     * 清理所有资源
     */
    @PreDestroy
    public void shutdown() {
        // 关闭调度器
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }

        // 销毁所有Invoker
        for (Map.Entry<InetSocketAddress, Invoker> entry : invokerMap.entrySet()) {
            Invoker invoker = entry.getValue();
            if (invoker != null) {
                invoker.destroy();
            }
        }
        invokerMap.clear();
        serviceAddressMap.clear();
        serviceStatusMap.clear();
        started = false;
        log.info("InvokerManager已关闭，所有资源已清理");
    }

    /**
     * 连接模式
     */
    public enum ConnectionMode {
        // 立即连接模式 - 发现地址后立即创建连接
        EAGER,
        // 延迟连接模式 - 只有在需要调用时才创建连接
        LAZY
    }

    /**
     * 服务状态类，记录服务重连信息
     */
    private static class ServiceStatus {
        // 服务地址
        final InetSocketAddress address;
        // 重试次数
        volatile int retryCount;
        // 最后一次连接尝试时间
        volatile long lastRetryTime;
        // 服务是否已确认下线
        volatile boolean confirmedDown;

        ServiceStatus(InetSocketAddress address) {
            this.address = address;
            this.retryCount = 0;
            this.lastRetryTime = 0;
            this.confirmedDown = false;
        }
    }
}