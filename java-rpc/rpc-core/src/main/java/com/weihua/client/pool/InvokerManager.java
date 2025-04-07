package com.weihua.client.pool;

import com.weihua.client.invoker.ChannelInvoker;
import com.weihua.client.invoker.Invoker;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import lombok.extern.log4j.Log4j2;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Invoker管理器，负责管理所有的Invoker实例
 */
@Log4j2
public class InvokerManager {
    // 单例
    private static final InvokerManager INSTANCE = new InvokerManager();

    // 默认每个服务地址的最大连接数
    private static final int DEFAULT_MAX_CONNECTIONS = 4;

    // 默认每个服务地址的初始连接数
    private static final int DEFAULT_INIT_CONNECTIONS = 4;

    // 按地址存储Invoker - 使用InetSocketAddress代替String作为键
    private final Map<InetSocketAddress, CopyOnWriteArrayList<Invoker>> invokerMap = new ConcurrentHashMap<>();

    // 连接计数器
    private final Map<InetSocketAddress, AtomicInteger> connectionCounter = new ConcurrentHashMap<>();

    // 每个地址的最大连接数
    private final Map<InetSocketAddress, Integer> maxConnectionsMap = new ConcurrentHashMap<>();

    // 每个地址的初始连接数
    private final Map<InetSocketAddress, Integer> initConnectionsMap = new ConcurrentHashMap<>();

    // 连接创建锁
    private final ReentrantLock lock = new ReentrantLock();

    // 客户端启动器
    private Bootstrap bootstrap;

    // 默认超时时间（秒）
    private int defaultTimeout = 5;

    // 连接健康检查和维护调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "invoker-maintenance-thread");
        t.setDaemon(true);
        return t;
    });

    // 是否已启动
    private volatile boolean started = false;

    private InvokerManager() {
    }

    public static InvokerManager getInstance() {
        return INSTANCE;
    }

    /**
     * 设置Bootstrap
     */
    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * 设置默认超时时间
     */
    public void setDefaultTimeout(int timeoutSeconds) {
        this.defaultTimeout = timeoutSeconds;
    }

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

        // 解析主机名为IP地址，确保不同主机名但指向同IP的情况能被正确处理
        InetAddress inetAddress = InetAddress.getByName(host);
        return new InetSocketAddress(inetAddress, port);
    }

    /**
     * 设置最大连接数
     */
    public void setMaxConnections(String address, int maxConnections) {
        try {
            InetSocketAddress socketAddress = parseAddress(address);
            maxConnectionsMap.put(socketAddress, maxConnections);
        } catch (Exception e) {
            log.error("设置最大连接数失败: {}", e.getMessage());
        }
    }

    /**
     * 设置初始连接数
     */
    public void setInitConnections(String address, int initConnections) {
        try {
            InetSocketAddress socketAddress = parseAddress(address);
            initConnectionsMap.put(socketAddress,
                    Math.min(initConnections, getMaxConnections(socketAddress)));
        } catch (Exception e) {
            log.error("设置初始连接数失败: {}", e.getMessage());
        }
    }

    /**
     * 获取最大连接数
     */
    public int getMaxConnections(InetSocketAddress address) {
        return maxConnectionsMap.getOrDefault(address, DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * 获取初始连接数
     */
    public int getInitConnections(InetSocketAddress address) {
        return initConnectionsMap.getOrDefault(address, DEFAULT_INIT_CONNECTIONS);
    }

    /**
     * 启动管理器，初始化连接维护任务
     */
    public void start() {
        if (!started) {
            synchronized (this) {
                if (!started) {
                    // 启动连接维护任务
                    scheduler.scheduleAtFixedRate(
                            this::maintenanceTask,
                            5, 30, TimeUnit.SECONDS);
                    started = true;
                    log.info("InvokerManager已启动，连接维护任务已开始");
                }
            }
        }
    }

    /**
     * 连接维护任务
     */
    private void maintenanceTask() {
        try {
            // 检查并维护各地址的连接
            for (Map.Entry<InetSocketAddress, CopyOnWriteArrayList<Invoker>> entry : invokerMap.entrySet()) {
                InetSocketAddress address = entry.getKey();
                CopyOnWriteArrayList<Invoker> invokers = entry.getValue();

                // 移除不可用的Invoker
                List<Invoker> unavailableInvokers = invokers.stream()
                        .filter(invoker -> !invoker.isAvailable())
                        .collect(Collectors.toList());

                for (Invoker invoker : unavailableInvokers) {
                    removeInvoker(address, invoker);
                    log.info("移除不可用的Invoker: {}", invoker.getId());
                }

                // 确保每个地址至少有初始连接数
                int current = connectionCounter.getOrDefault(address, new AtomicInteger(0)).get();
                int target = getInitConnections(address);

                if (current < target) {
                    log.info("地址 {}:{} 当前连接数 {}, 低于目标初始连接数 {}, 尝试预建立连接",
                            address.getHostString(), address.getPort(), current, target);

                    try {
                        for (int i = current; i < target; i++) {
                            getInvoker(address);
                        }
                    } catch (Exception e) {
                        log.warn("为地址 {}:{} 预建立连接失败: {}",
                                address.getHostString(), address.getPort(), e.getMessage());
                    }
                }
            }

            // 打印当前连接状态统计
            printState();

        } catch (Exception e) {
            log.error("执行连接维护任务时发生错误", e);
        }
    }

    /**
     * 向地址列表添加新地址
     */
    public void addAddresses(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        for (String address : addresses) {
            try {
                // 转换为InetSocketAddress
                InetSocketAddress socketAddress = parseAddress(address);

                // 仅处理新地址
                if (!invokerMap.containsKey(socketAddress)) {
                    // 为新地址预建立初始连接
                    int initCount = getInitConnections(socketAddress);
                    log.info("为新地址 {}:{} 预建立 {} 个连接",
                            socketAddress.getHostString(), socketAddress.getPort(), initCount);

                    for (int i = 0; i < initCount; i++) {
                        try {
                            getInvoker(socketAddress);
                        } catch (Exception e) {
                            log.warn("为新地址 {}:{} 预建立连接失败: {}",
                                    socketAddress.getHostString(), socketAddress.getPort(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("处理地址 {} 失败: {}", address, e.getMessage());
            }
        }
    }

    /**
     * 移除不存在的地址
     */
    public void removeAddresses(List<String> currentAddresses) {
        if (currentAddresses == null) {
            return;
        }

        try {
            // 转换当前有效地址到InetSocketAddress
            List<InetSocketAddress> currentSocketAddresses = new ArrayList<>();
            for (String address : currentAddresses) {
                try {
                    currentSocketAddresses.add(parseAddress(address));
                } catch (Exception e) {
                    log.warn("转换地址 {} 失败: {}", address, e.getMessage());
                }
            }

            // 获取所有需要移除的地址
            List<InetSocketAddress> addressesToRemove = new ArrayList<>();
            for (InetSocketAddress existingAddress : invokerMap.keySet()) {
                if (!currentSocketAddresses.contains(existingAddress)) {
                    addressesToRemove.add(existingAddress);
                }
            }

            // 移除不再可用的地址
            for (InetSocketAddress address : addressesToRemove) {
                CopyOnWriteArrayList<Invoker> invokers = invokerMap.remove(address);
                if (invokers != null) {
                    for (Invoker invoker : invokers) {
                        invoker.destroy();
                    }
                    connectionCounter.remove(address);
                    log.info("移除下线地址: {}:{}, 关闭 {} 个连接",
                            address.getHostString(), address.getPort(), invokers.size());
                }
            }
        } catch (Exception e) {
            log.error("移除地址失败: {}", e.getMessage());
        }
    }

    /**
     * 获取或创建Invoker (String地址版)
     */
    public Invoker getInvoker(String address) throws Exception {
        return getInvoker(parseAddress(address));
    }

    /**
     * 获取或创建Invoker (InetSocketAddress版)
     * 
     * @param socketAddress 服务地址
     * @return Invoker实例
     */
    public Invoker getInvoker(InetSocketAddress socketAddress) throws Exception {
        // 先尝试获取已有的活跃Invoker
        Invoker invoker = getLeastActiveInvoker(socketAddress);
        if (invoker != null) {
            return invoker;
        }

        // 获取当前连接数
        int currentCount = connectionCounter.getOrDefault(socketAddress, new AtomicInteger(0)).get();
        int maxConnections = getMaxConnections(socketAddress);

        // 如果未达到最大连接数，创建新连接
        if (currentCount < maxConnections) {
            lock.lock();
            try {
                // 双重检查
                currentCount = connectionCounter.getOrDefault(socketAddress, new AtomicInteger(0)).get();
                if (currentCount < maxConnections) {
                    // 创建新连接
                    if (bootstrap == null) {
                        throw new IllegalStateException("Bootstrap未设置，无法创建连接");
                    }

                    Channel channel = bootstrap.connect(socketAddress).sync().channel();
                    if (channel != null && channel.isActive()) {
                        // 创建Invoker并添加到管理器
                        Invoker newInvoker = new ChannelInvoker(channel, defaultTimeout);
                        addInvoker(socketAddress, newInvoker);
                        return newInvoker;
                    }
                    throw new Exception("创建连接失败");
                }
            } finally {
                lock.unlock();
            }
        }

        // 如果已达到最大连接数但没有可用的Invoker，说明所有连接都不可用
        throw new Exception("无可用连接，且已达到最大连接数限制: " + maxConnections);
    }

    /**
     * 添加Invoker
     */
    private void addInvoker(InetSocketAddress address, Invoker invoker) {
        invokerMap.computeIfAbsent(address, k -> new CopyOnWriteArrayList<>()).add(invoker);
        connectionCounter.computeIfAbsent(address, k -> new AtomicInteger(0)).incrementAndGet();
        log.info("添加新Invoker到连接池，地址: {}:{}, 当前连接数: {}",
                address.getHostString(), address.getPort(),
                connectionCounter.get(address).get());
    }

    /**
     * 获取最少活跃请求的Invoker
     */
    private Invoker getLeastActiveInvoker(InetSocketAddress address) {
        CopyOnWriteArrayList<Invoker> invokers = invokerMap.get(address);
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }

        // 筛选可用的Invoker
        List<Invoker> availableInvokers = invokers.stream()
                .filter(Invoker::isAvailable)
                .collect(Collectors.toList());

        if (availableInvokers.isEmpty()) {
            return null;
        }

        // 查找活跃请求数最少的Invoker
        Invoker leastActive = availableInvokers.get(0);
        for (Invoker invoker : availableInvokers) {
            if (invoker.getActiveCount() < leastActive.getActiveCount()) {
                leastActive = invoker;
            }
        }

        log.debug("选择最少活跃请求Invoker: {}, 活跃请求数: {}",
                leastActive.getId(), leastActive.getActiveCount());
        return leastActive;
    }

    /**
     * 获取指定地址的所有可用Invoker
     */
    public List<Invoker> getAvailableInvokers(InetSocketAddress address) {
        CopyOnWriteArrayList<Invoker> invokers = invokerMap.get(address);
        if (invokers == null) {
            return new ArrayList<>();
        }
        return invokers.stream()
                .filter(Invoker::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * 字符串地址版本的可用Invoker获取
     */
    public List<Invoker> getAvailableInvokers(String address) {
        try {
            return getAvailableInvokers(parseAddress(address));
        } catch (Exception e) {
            log.error("获取可用Invoker失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有地址的可用Invoker
     */
    public List<Invoker> getAllAvailableInvokers() {
        List<Invoker> result = new ArrayList<>();
        for (CopyOnWriteArrayList<Invoker> invokers : invokerMap.values()) {
            invokers.stream()
                    .filter(Invoker::isAvailable)
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * 移除Invoker (InetSocketAddress版)
     */
    public void removeInvoker(InetSocketAddress address, Invoker invoker) {
        if (invoker == null) {
            return;
        }

        CopyOnWriteArrayList<Invoker> invokers = invokerMap.get(address);
        if (invokers != null) {
            invokers.remove(invoker);
            connectionCounter.get(address).decrementAndGet();
            invoker.destroy();
            log.info("移除Invoker, 地址: {}:{}, 当前连接数: {}",
                    address.getHostString(), address.getPort(),
                    connectionCounter.get(address).get());
        }
    }

    /**
     * 移除Invoker (String地址版)
     */
    public void removeInvoker(String address, Invoker invoker) {
        try {
            removeInvoker(parseAddress(address), invoker);
        } catch (Exception e) {
            log.error("移除Invoker失败: {}", e.getMessage());
        }
    }

    /**
     * 清理所有资源
     */
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
        for (Map.Entry<InetSocketAddress, CopyOnWriteArrayList<Invoker>> entry : invokerMap.entrySet()) {
            for (Invoker invoker : entry.getValue()) {
                invoker.destroy();
            }
            entry.getValue().clear();
            connectionCounter.get(entry.getKey()).set(0);
        }
        invokerMap.clear();
        connectionCounter.clear();
        started = false;
        log.info("InvokerManager已关闭，所有资源已清理");
    }

    /**
     * 打印状态信息
     */
    public void printState() {
        for (Map.Entry<InetSocketAddress, AtomicInteger> entry : connectionCounter.entrySet()) {
            InetSocketAddress address = entry.getKey();
            int count = entry.getValue().get();
            int maxConn = getMaxConnections(address);

            // 获取地址的活跃请求总数和可用连接数
            List<Invoker> availableInvokers = getAvailableInvokers(address);
            int activeRequests = 0;
            double avgResponseTime = 0;
            double avgSuccessRate = 0;

            for (Invoker invoker : availableInvokers) {
                activeRequests += invoker.getActiveCount();
                avgResponseTime += invoker.getAvgResponseTime();
                avgSuccessRate += invoker.getSuccessRate();
            }

            if (!availableInvokers.isEmpty()) {
                avgResponseTime /= availableInvokers.size();
                avgSuccessRate /= availableInvokers.size();
            }

            log.info("连接池状态 - 地址: {}:{}, 连接数: {}/{}, 可用: {}, 活跃请求: {}, 平均响应时间: {}ms, 成功率: {}%",
                    address.getHostString(), address.getPort(),
                    count, maxConn, availableInvokers.size(), activeRequests,
                    String.format("%.2f", avgResponseTime),
                    String.format("%.2f", avgSuccessRate * 100));
        }
    }
}