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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Invoker管理器
 * 采用Dubbo风格的固定连接模型，每个地址只维护一个固定连接
 */
@Log4j2
public class InvokerManager {
    // 单例
    private static final InvokerManager INSTANCE = new InvokerManager();

    // 按地址存储Invoker（每个地址一个固定Invoker）
    private final Map<InetSocketAddress, Invoker> invokerMap = new ConcurrentHashMap<>();

    // 连接创建锁
    private final ReentrantLock lock = new ReentrantLock();

    // 客户端启动器
    private Bootstrap bootstrap;

    // 默认超时时间（秒）
    private int defaultTimeout = 5;

    // 连接健康检查调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "invoker-health-check");
        t.setDaemon(true);
        return t;
    });

    // 是否已启动
    private volatile boolean started = false;

    // 重连间隔（毫秒）
    private int reconnectInterval = 5000;

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
     * 设置重连间隔
     */
    public void setReconnectInterval(int intervalMillis) {
        this.reconnectInterval = intervalMillis;
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

        // 解析主机名为IP地址
        InetAddress inetAddress = InetAddress.getByName(host);
        return new InetSocketAddress(inetAddress, port);
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
            for (Map.Entry<InetSocketAddress, Invoker> entry : invokerMap.entrySet()) {
                InetSocketAddress address = entry.getKey();
                Invoker invoker = entry.getValue();

                // 如果连接不可用，尝试重连
                if (!invoker.isAvailable()) {
                    log.info("检测到连接不可用，准备重新连接: {}:{}",
                            address.getHostString(), address.getPort());

                    try {
                        // 销毁旧连接
                        invoker.destroy();

                        // 创建新连接（异步）
                        createNewConnection(address);
                    } catch (Exception e) {
                        log.warn("重新连接失败: {}:{}, 错误: {}",
                                address.getHostString(), address.getPort(), e.getMessage());
                    }
                }
            }

            // 打印状态
            printState();
        } catch (Exception e) {
            log.error("执行连接健康检查任务时出错", e);
        }
    }

    /**
     * 创建新连接
     */
    private void createNewConnection(InetSocketAddress address) {
        if (bootstrap == null) {
            log.error("Bootstrap未设置，无法创建连接");
            return;
        }

        // 异步连接创建
        bootstrap.connect(address).addListener(future -> {
            if (future.isSuccess()) {
                Channel channel = (Channel) future.getNow();
                Invoker newInvoker = new ChannelInvoker(channel, defaultTimeout);

                // 替换旧连接
                invokerMap.put(address, newInvoker);
                log.info("成功重新连接到服务: {}:{}",
                        address.getHostString(), address.getPort());
            } else {
                log.warn("连接失败: {}:{}, 将在{}ms后重试",
                        address.getHostString(), address.getPort(), reconnectInterval);

                // 延迟后重试
                scheduler.schedule(() -> createNewConnection(address),
                        reconnectInterval, TimeUnit.MILLISECONDS);
            }
        });
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
                InetSocketAddress socketAddress = parseAddress(address);

                // 使用锁保护整个检查和创建连接的过程
                lock.lock();
                try {
                    if (!invokerMap.containsKey(socketAddress)) {
                        log.info("为新地址创建连接: {}:{}",
                                socketAddress.getHostString(), socketAddress.getPort());

                        // 创建连接
                        Channel channel = bootstrap.connect(socketAddress).sync().channel();
                        if (channel != null && channel.isActive()) {
                            Invoker newInvoker = new ChannelInvoker(channel, defaultTimeout);
                            invokerMap.put(socketAddress, newInvoker);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                log.error("处理地址失败: {}, 错误: {}", address, e.getMessage());
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
                    log.warn("转换地址失败: {}, 错误: {}", address, e.getMessage());
                }
            }

            // 获取需要移除的地址
            List<InetSocketAddress> addressesToRemove = new ArrayList<>();
            for (InetSocketAddress existingAddress : invokerMap.keySet()) {
                if (!currentSocketAddresses.contains(existingAddress)) {
                    addressesToRemove.add(existingAddress);
                }
            }

            // 移除不再可用的地址
            for (InetSocketAddress address : addressesToRemove) {
                Invoker invoker = invokerMap.remove(address);
                if (invoker != null) {
                    invoker.destroy();
                    log.info("移除下线地址连接: {}:{}",
                            address.getHostString(), address.getPort());
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
     */
    public Invoker getInvoker(InetSocketAddress socketAddress) throws Exception {
        // 先检查是否已有可用连接
        Invoker invoker = invokerMap.get(socketAddress);
        if (invoker != null && invoker.isAvailable()) {
            return invoker;
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
            if (bootstrap == null) {
                throw new IllegalStateException("Bootstrap未设置，无法创建连接");
            }

            log.info("创建新连接: {}:{}", socketAddress.getHostString(), socketAddress.getPort());
            Channel channel = bootstrap.connect(socketAddress).sync().channel();

            if (channel != null && channel.isActive()) {
                Invoker newInvoker = new ChannelInvoker(channel, defaultTimeout);

                // 替换旧连接（如果有）
                Invoker oldInvoker = invokerMap.put(socketAddress, newInvoker);
                if (oldInvoker != null) {
                    oldInvoker.destroy();
                }

                return newInvoker;
            }

            throw new Exception("创建连接失败");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取指定地址的所有可用Invoker (为兼容API，只返回单个固定连接)
     */
    public List<Invoker> getAvailableInvokers(InetSocketAddress address) {
        List<Invoker> result = new ArrayList<>();
        Invoker invoker = invokerMap.get(address);
        if (invoker != null && invoker.isAvailable()) {
            result.add(invoker);
        }
        return result;
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
        for (Invoker invoker : invokerMap.values()) {
            if (invoker.isAvailable()) {
                result.add(invoker);
            }
        }
        return result;
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
        for (Map.Entry<InetSocketAddress, Invoker> entry : invokerMap.entrySet()) {
            Invoker invoker = entry.getValue();
            if (invoker != null) {
                invoker.destroy();
            }
        }
        invokerMap.clear();
        started = false;
        log.info("InvokerManager已关闭，所有资源已清理");
    }

    /**
     * 打印状态信息
     */
    public void printState() {
        for (Map.Entry<InetSocketAddress, Invoker> entry : invokerMap.entrySet()) {
            InetSocketAddress address = entry.getKey();
            Invoker invoker = entry.getValue();

            if (invoker != null) {
                log.info("连接状态 - 地址: {}:{}, 可用: {}, 活跃请求: {}, 平均响应时间: {}ms, 成功率: {}%",
                        address.getHostString(), address.getPort(),
                        invoker.isAvailable(), invoker.getActiveCount(),
                        String.format("%.2f", invoker.getAvgResponseTime()),
                        String.format("%.2f", invoker.getSuccessRate() * 100));
            }
        }
    }
}