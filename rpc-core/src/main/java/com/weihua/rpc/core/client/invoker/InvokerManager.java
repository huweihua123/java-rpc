package com.weihua.rpc.core.client.invoker;

import com.weihua.rpc.core.client.cache.ServiceAddressCache;
import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.netty.NettyRpcClient;
import com.weihua.rpc.core.util.AddressUtils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
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
    // 连接配置
    private static final int HEALTH_CHECK_INITIAL_DELAY = 5;
    private static final int HEALTH_CHECK_INTERVAL = 10;
    private static final int IDLE_CHECK_INITIAL_DELAY = 60;
    private static final int IDLE_CHECK_INTERVAL = 60;
    private static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000; // 10分钟
    private static final int MAX_HEALTH_CHECKS_PER_RUN = 50;

    // 按地址存储Invoker包装器（整合连接和状态）
    private final Map<InetSocketAddress, InvokerWrapper> invokerMap = new ConcurrentHashMap<>();

    // 按服务名存储关联的地址列表
    private final Map<String, Set<InetSocketAddress>> serviceAddressMap = new ConcurrentHashMap<>();

    // 分段锁 - 每个地址一个锁
    private final ConcurrentMap<InetSocketAddress, ReentrantLock> addressLocks = new ConcurrentHashMap<>();

    // 待健康检查的地址队列
    private final ConcurrentLinkedQueue<InetSocketAddress> healthCheckQueue = new ConcurrentLinkedQueue<>();

    @Autowired
    private ClientConfig clientConfig;

    @Autowired
    @Lazy
    private NettyRpcClient nettyRpcClient;

    @Autowired
    private ServiceAddressCache addressCache;

    // 任务调度器
    private ScheduledExecutorService scheduler;

    // 配置参数
    private InvokerManagerConfig config;

    /**
     * 获取指定地址的锁
     */
    private ReentrantLock getLockForAddress(InetSocketAddress address) {
        return addressLocks.computeIfAbsent(address, k -> new ReentrantLock());
    }

    @PostConstruct
    public void init() {
        // 创建配置
        this.config = new InvokerManagerConfig(clientConfig);

        // 创建调度器
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "invoker-manager-scheduler");
            t.setDaemon(true);
            return t;
        });

        // 启动连接健康检查任务
        scheduler.scheduleAtFixedRate(
                this::healthCheckTask,
                HEALTH_CHECK_INITIAL_DELAY,
                HEALTH_CHECK_INTERVAL,
                TimeUnit.SECONDS);

        // 启动闲置连接清理任务
        scheduler.scheduleAtFixedRate(
                this::cleanupIdleConnections,
                IDLE_CHECK_INITIAL_DELAY,
                IDLE_CHECK_INTERVAL,
                TimeUnit.SECONDS);

        log.info("InvokerManager初始化完成，连接模式：{}, 重试策略: {}",
                config.getConnectionMode(), config.getBackoffStrategy());
    }

    /**
     * 清理闲置连接
     */
    private void cleanupIdleConnections() {
        try {
            long now = System.currentTimeMillis();
            List<InetSocketAddress> idleAddresses = new ArrayList<>();

            // 找出所有闲置连接
            for (Map.Entry<InetSocketAddress, InvokerWrapper> entry : invokerMap.entrySet()) {
                InetSocketAddress address = entry.getKey();
                InvokerWrapper wrapper = entry.getValue();

                if (wrapper.isIdle(IDLE_TIMEOUT_MS)) {
                    idleAddresses.add(address);
                }
            }

            // 移除闲置连接
            for (InetSocketAddress address : idleAddresses) {
                log.info("清理闲置连接: {}:{}", address.getHostString(), address.getPort());
                removeInvoker(address);
            }

            if (!idleAddresses.isEmpty()) {
                log.info("已清理{}个闲置连接", idleAddresses.size());
            }
        } catch (Exception e) {
            log.error("清理闲置连接时出错", e);
        }
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
            // 解析地址字符串
            List<InetSocketAddress> addresses = AddressUtils.parseAddresses(addressStrings);

            // 获取服务当前地址集
            Set<InetSocketAddress> addressSet = serviceAddressMap.computeIfAbsent(
                    serviceName, k -> ConcurrentHashMap.newKeySet());

            // 计算需要移除的地址
            Set<InetSocketAddress> removedAddresses = new HashSet<>(addressSet);
            removedAddresses.removeAll(addresses);

            // 计算需要新增的地址
            Set<InetSocketAddress> newAddresses = new HashSet<>(addresses);
            newAddresses.removeAll(addressSet);

            // 处理需要移除的地址
            for (InetSocketAddress address : removedAddresses) {
                addressSet.remove(address);

                InvokerWrapper wrapper = invokerMap.get(address);
                if (wrapper != null) {
                    wrapper.removeUsingService(serviceName);

                    // 如果没有服务使用此连接，则在下次闲置检查时会被清理
                    if (!wrapper.isUsedByAnyService()) {
                        log.info("地址[{}:{}]不再被任何服务使用，将在闲置后清理",
                                address.getHostString(), address.getPort());
                    }
                }
            }

            // 处理需要新增的地址
            for (InetSocketAddress address : newAddresses) {
                addressSet.add(address);

                // 获取或创建包装器
                InvokerWrapper wrapper = invokerMap.get(address);
                if (wrapper == null) {
                    // 先添加地址，连接会在后续创建
                    wrapper = new InvokerWrapper(null, address);
                    invokerMap.put(address, wrapper);
                }

                // 标记服务使用此地址
                wrapper.addUsingService(serviceName);

                // EAGER模式下立即创建连接
                if (config.getConnectionMode() == ConnectionMode.EAGER) {
                    try {
                        createOrGetInvoker(address);
                    } catch (Exception e) {
                        log.warn("预连接服务[{}]地址[{}:{}]失败: {}",
                                serviceName, address.getHostString(), address.getPort(), e.getMessage());

                        // 添加到健康检查队列
                        addToHealthCheckQueue(address);
                    }
                }
            }

        } catch (Exception e) {
            log.error("更新服务[{}]地址列表时出错: {}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * 添加地址到健康检查队列
     */
    private void addToHealthCheckQueue(InetSocketAddress address) {
        if (!healthCheckQueue.contains(address)) {
            healthCheckQueue.offer(address);
        }
    }

    /**
     * 连接健康检查任务
     */
    private void healthCheckTask() {
        try {
            int checkedCount = 0;

            // 优先检查队列中的地址
            while (checkedCount < MAX_HEALTH_CHECKS_PER_RUN && !healthCheckQueue.isEmpty()) {
                InetSocketAddress address = healthCheckQueue.poll();
                if (checkAndRecoverConnection(address)) {
                    checkedCount++;
                }
            }

            // 如果还有额度，检查所有连接
            if (checkedCount < MAX_HEALTH_CHECKS_PER_RUN) {
                for (Map.Entry<InetSocketAddress, InvokerWrapper> entry : invokerMap.entrySet()) {
                    if (checkedCount >= MAX_HEALTH_CHECKS_PER_RUN) {
                        break;
                    }

                    InetSocketAddress address = entry.getKey();
                    InvokerWrapper wrapper = entry.getValue();

                    // 只检查不可用但未确认下线的连接
                    if (!wrapper.isAvailable() && !wrapper.isConfirmedDown() && wrapper.isUsedByAnyService()) {
                        if (checkAndRecoverConnection(address)) {
                            checkedCount++;
                        }
                    }
                }
            }

            // 如果开启调试日志，打印状态信息
            if (log.isDebugEnabled()) {
                printState();
            }
        } catch (Exception e) {
            log.error("执行连接健康检查任务时出错", e);
        }
    }

    /**
     * 检查并尝试恢复连接
     * 
     * @return 是否执行了实际检查
     */
    private boolean checkAndRecoverConnection(InetSocketAddress address) {
        InvokerWrapper wrapper = invokerMap.get(address);
        if (wrapper == null || wrapper.isConfirmedDown() || !wrapper.isUsedByAnyService()) {
            return false;
        }

        // 检查重试次数
        if (wrapper.getRetryCount() >= config.getMaxRetryAttempts()) {
            log.warn("连接[{}:{}]已达到最大重试次数({}次)，不再重试",
                    address.getHostString(), address.getPort(), config.getMaxRetryAttempts());
            return false;
        }

        // 检查是否应该重试
        if (!config.getBackoffStrategy().shouldRetry(
                wrapper.getLastRetryTime(), wrapper.getRetryCount(), config.getMaxRetryAttempts())) {
            return false;
        }

        int backoffTime = config.getBackoffStrategy().calculateDelayMillis(wrapper.getRetryCount());

        log.info("检测到连接不可用，准备重新连接(第{}次尝试): {}:{}, 退避时间: {}ms",
                wrapper.getRetryCount() + 1, address.getHostString(), address.getPort(), backoffTime);

        try {
            // 销毁旧连接
            Invoker oldInvoker = wrapper.getInvoker();
            if (oldInvoker != null) {
                oldInvoker.destroy();
            }

            // 更新重试状态
            wrapper.incrementRetryCount();
            wrapper.setLastRetryTime(System.currentTimeMillis());

            // 创建新连接
            createNewConnection(address);
            return true;
        } catch (Exception e) {
            log.warn("重新连接失败: {}:{}, 错误: {}",
                    address.getHostString(), address.getPort(), e.getMessage());
            return true;
        }
    }

    /**
     * 创建新连接（异步）
     */
    private void createNewConnection(InetSocketAddress address) {
        Bootstrap bootstrap = nettyRpcClient.getBootstrap();
        if (bootstrap == null) {
            log.error("Bootstrap未设置，无法创建连接");
            return;
        }

        InvokerWrapper wrapper = invokerMap.get(address);
        if (wrapper == null) {
            wrapper = new InvokerWrapper(null, address);
            invokerMap.put(address, wrapper);
        }

        if (wrapper.isConfirmedDown()) {
            log.info("服务地址[{}:{}]已确认下线，不再尝试连接",
                    address.getHostString(), address.getPort());
            return;
        }

        // 异步连接
        InvokerWrapper finalWrapper = wrapper;
        bootstrap.connect(address).addListener(future -> {
            if (future.isSuccess()) {
                try {
                    Channel channel = ((io.netty.channel.ChannelFuture) future).channel();

                    if (channel != null && channel.isActive()) {
                        Invoker newInvoker = new ChannelInvoker(channel);

                        // 更新包装器
                        finalWrapper.setInvoker(newInvoker);
                        finalWrapper.resetRetry();

                        log.info("成功连接到服务: {}:{}",
                                address.getHostString(), address.getPort());
                    } else {
                        handleConnectionFailure(finalWrapper, new IllegalStateException("Channel无效或未激活"));
                        log.warn("连接异常: {}:{}, Channel无效或未激活",
                                address.getHostString(), address.getPort());
                    }
                } catch (Exception e) {
                    handleConnectionFailure(finalWrapper, e);
                    log.warn("创建Invoker失败: {}:{}, 错误: {}",
                            address.getHostString(), address.getPort(), e.getMessage());
                }
            } else {
                Throwable cause = future.cause();
                handleConnectionFailure(finalWrapper, cause);

                String errorMsg = cause != null ? cause.getMessage() : "未知错误";
                log.warn("连接失败: {}:{}, 错误: {}",
                        address.getHostString(), address.getPort(), errorMsg);
            }
        });
    }

    /**
     * 处理连接失败
     */
    private void handleConnectionFailure(InvokerWrapper wrapper, Throwable cause) {
        if (wrapper != null) {
            wrapper.incrementRetryCount();
            wrapper.setLastRetryTime(System.currentTimeMillis());

            // 检查是否达到最大重试次数
            if (wrapper.getRetryCount() >= config.getMaxRetryAttempts()) {
                wrapper.setConfirmedDown(true);
                InetSocketAddress address = wrapper.getAddress();
                log.warn("连接[{}:{}]已达到最大重试次数({})，标记为已确认下线",
                        address.getHostString(), address.getPort(), config.getMaxRetryAttempts());

                // 清理已下线服务
                cleanupDownedService(address);
                return;
            }

            // 根据异常类型处理
            int backoffTime = calculateBackoffTimeBasedOnException(wrapper, cause);

            InetSocketAddress address = wrapper.getAddress();
            log.warn("连接失败: {}:{}, 将在{}ms后重试, 已尝试{}次/最大{}次",
                    address.getHostString(), address.getPort(),
                    backoffTime, wrapper.getRetryCount(), config.getMaxRetryAttempts());

            // 安排下次重试
            scheduler.schedule(() -> createNewConnection(address),
                    backoffTime, TimeUnit.MILLISECONDS);

            // 添加到健康检查队列
            addToHealthCheckQueue(address);
        }
    }

    /**
     * 根据异常类型计算退避时间
     */
    private int calculateBackoffTimeBasedOnException(InvokerWrapper wrapper, Throwable cause) {
        int baseBackoffTime = config.getBackoffStrategy().calculateDelayMillis(wrapper.getRetryCount());

        // 根据异常类型可以做特殊处理
        if (cause instanceof ConnectException) {
            // 连接被拒绝，可能是服务未启动，延长退避时间
            return Math.min(baseBackoffTime * 2, 60000); // 最长1分钟
        } else if (cause instanceof SocketTimeoutException) {
            // 连接超时，可能是网络波动，适当减少退避时间
            return Math.max(baseBackoffTime / 2, 1000); // 最短1秒
        }

        return baseBackoffTime;
    }

    /**
     * 清理已下线服务资源
     */
    private void cleanupDownedService(InetSocketAddress address) {
        try {
            InvokerWrapper wrapper = invokerMap.get(address);
            if (wrapper == null) {
                return;
            }

            // 1. 获取使用该地址的所有服务
            Set<String> affectedServices = new HashSet<>(wrapper.getUsingServices());

            // 2. 从每个服务的地址列表中移除
            for (String serviceName : affectedServices) {
                Set<InetSocketAddress> addresses = serviceAddressMap.get(serviceName);
                if (addresses != null && addresses.remove(address)) {
                    log.info("从服务[{}]的地址列表中移除已下线地址: {}:{}",
                            serviceName, address.getHostString(), address.getPort());
                }

                // 清空服务引用
                wrapper.removeUsingService(serviceName);
            }

            // 3. 如果不再被任何服务使用，从Map中移除并销毁
            if (!wrapper.isUsedByAnyService()) {
                removeInvoker(address);
            }
        } catch (Exception e) {
            log.error("清理已下线服务资源时出错: {}:{} - {}",
                    address.getHostString(), address.getPort(), e.getMessage());
        }
    }

    /**
     * 创建或获取Invoker
     */
    public Invoker createOrGetInvoker(InetSocketAddress socketAddress) throws Exception {
        // 快速路径：无锁检查
        InvokerWrapper wrapper = invokerMap.get(socketAddress);
        if (wrapper != null && wrapper.isAvailable()) {
            return wrapper.getInvoker(); // 有可用连接，直接返回
        }

        // 失败路径检查
        if (wrapper != null && wrapper.isConfirmedDown()) {
            throw new IllegalStateException("服务地址已确认下线，无法创建连接");
        }

        // 获取锁并再次检查
        return createInvokerWithLock(socketAddress);
    }

    /**
     * 在锁保护下创建Invoker
     */
    private Invoker createInvokerWithLock(InetSocketAddress socketAddress) throws Exception {
        ReentrantLock lock = getLockForAddress(socketAddress);
        lock.lock();
        try {
            // 锁内二次检查
            InvokerWrapper wrapper = invokerMap.get(socketAddress);
            if (wrapper != null && wrapper.isAvailable()) {
                return wrapper.getInvoker(); // 其他线程已创建，直接返回
            }

            // 创建临时wrapper，但不立即放入map
            InvokerWrapper newWrapper;
            if (wrapper == null) {
                newWrapper = new InvokerWrapper(null, socketAddress);
            } else {
                newWrapper = wrapper; // 重用现有wrapper
            }

            // 创建新连接
            Bootstrap bootstrap = nettyRpcClient.getBootstrap();
            if (bootstrap == null) {
                throw new IllegalStateException("Bootstrap未设置，无法创建连接");
            }

            log.info("创建新连接: {}:{}", socketAddress.getHostString(), socketAddress.getPort());

            try {
                // 同步连接
                Channel channel = bootstrap.connect(socketAddress).sync().channel();

                if (channel != null && channel.isActive()) {
                    Invoker newInvoker = new ChannelInvoker(channel);

                    // 更新包装器
                    newWrapper.setInvoker(newInvoker);
                    newWrapper.resetRetry();

                    // 完全初始化后才放入map
                    invokerMap.put(socketAddress, newWrapper);

                    return newInvoker;
                }

                throw new Exception("创建连接失败，Channel无效或未激活");
            } catch (Exception e) {
                // 只有在完全初始化后才会放入map，因此连接失败无需从map中移除
                // 但如果是复用的wrapper，需要保留在map中
                if (wrapper == null) {
                    invokerMap.remove(socketAddress);
                }

                log.warn("连接失败: {}:{}, 错误: {}",
                        socketAddress.getHostString(), socketAddress.getPort(), e.getMessage());
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取服务的所有可用Invoker
     */
    public List<Invoker> getInvokers(String serviceName) {
        List<Invoker> result = new ArrayList<>();

        // 获取服务地址列表
        Set<InetSocketAddress> addresses = serviceAddressMap.get(serviceName);
        log.info("获取服务[{}]的Invoker列表，地址数量: {}",
                serviceName, addresses != null ? addresses.size() : 0);

        // 如果没有地址，尝试从缓存获取
        if (addresses == null || addresses.isEmpty()) {
            List<String> addressStrings = addressCache.getAddresses(serviceName);
            if (addressStrings != null && !addressStrings.isEmpty()) {
                updateServiceAddresses(serviceName, addressStrings);
                return getInvokers(serviceName);
            }
            return result;
        }

        // 遍历地址获取Invoker
        for (InetSocketAddress address : addresses) {
            try {
                InvokerWrapper wrapper = invokerMap.get(address);

                // 跳过已确认下线的地址
                if (wrapper != null && wrapper.isConfirmedDown()) {
                    continue;
                }

                // 根据连接模式处理
                if (config.getConnectionMode() == ConnectionMode.EAGER) {
                    // EAGER模式 - 直接从Map获取
                    if (wrapper != null && wrapper.isAvailable()) {
                        result.add(wrapper.getInvoker());
                        wrapper.updateLastActiveTime(); // 更新活动时间
                    }
                } else {
                    // LAZY模式 - 按需创建
                    log.debug("使用LAZY模式获取Invoker: {}:{}",
                            address.getHostString(), address.getPort());
                    Invoker invoker = createOrGetInvoker(address);
                    if (invoker != null && invoker.isAvailable()) {
                        result.add(invoker);

                        // 确保包装器记录服务使用关系
                        wrapper = invokerMap.get(address);
                        if (wrapper != null) {
                            wrapper.addUsingService(serviceName);
                            wrapper.updateLastActiveTime();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("为服务[{}]获取地址[{}:{}]的Invoker失败: {}",
                        serviceName, address.getHostString(), address.getPort(), e.getMessage());

                // 添加到健康检查队列
                addToHealthCheckQueue(address);
            }
        }

        return result;
    }

    /**
     * 移除并销毁Invoker
     */
    public void removeInvoker(InetSocketAddress address) {
        ReentrantLock lock = getLockForAddress(address);
        lock.lock();
        try {
            InvokerWrapper wrapper = invokerMap.remove(address);
            if (wrapper != null) {
                Invoker invoker = wrapper.getInvoker();
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

            // 从锁Map中移除
            addressLocks.remove(address);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 打印连接状态
     */
    public void printState() {
        for (Map.Entry<InetSocketAddress, InvokerWrapper> entry : invokerMap.entrySet()) {
            InetSocketAddress address = entry.getKey();
            InvokerWrapper wrapper = entry.getValue();
            Invoker invoker = wrapper.getInvoker();

            if (invoker != null) {
                log.info("连接状态 - 地址: {}:{}, 可用: {}, 活跃请求: {}, 总请求: {}, " +
                        "平均响应时间: {}ms, 成功率: {}%, 重试次数: {}, 使用服务: {}, {}",
                        address.getHostString(), address.getPort(),
                        wrapper.isAvailable(), invoker.getActiveCount(),
                        invoker.getRequestCount(),
                        String.format("%.2f", invoker.getAvgResponseTime()),
                        String.format("%.2f", invoker.getSuccessRate() * 100),
                        wrapper.getRetryCount(),
                        String.join(",", wrapper.getUsingServices()),
                        wrapper.isConfirmedDown() ? "已确认下线" : "");
            } else {
                log.info("连接状态 - 地址: {}:{}, 无有效连接, 重试次数: {}, 使用服务: {}, {}",
                        address.getHostString(), address.getPort(),
                        wrapper.getRetryCount(),
                        String.join(",", wrapper.getUsingServices()),
                        wrapper.isConfirmedDown() ? "已确认下线" : "");
            }
        }
    }

    /**
     * 关闭并清理资源
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
        for (Map.Entry<InetSocketAddress, InvokerWrapper> entry : invokerMap.entrySet()) {
            Invoker invoker = entry.getValue().getInvoker();
            if (invoker != null) {
                try {
                    invoker.destroy();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
        }

        // 清空所有集合
        invokerMap.clear();
        serviceAddressMap.clear();
        addressLocks.clear();
        healthCheckQueue.clear();

        log.info("InvokerManager已关闭，所有资源已清理");
    }

    /**
     * 连接模式枚举
     */
    public enum ConnectionMode {
        // 立即连接模式
        EAGER,
        // 延迟连接模式
        LAZY
    }
}