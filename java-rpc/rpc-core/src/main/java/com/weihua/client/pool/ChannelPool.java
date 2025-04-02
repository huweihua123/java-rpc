/*
 * @Author: weihua hu
 * @Date: 2025-04-02 22:23:59
 * @LastEditTime: 2025-04-02 22:27:47
 * @LastEditors: weihua hu
 * @Description: 连接池 负责管理连接资源，优化复用
 */
package com.weihua.client.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Netty 客户端连接池，管理与服务端的连接资源
 */
@Log4j2
public class ChannelPool {

    // 默认每个服务地址的最大连接数
    private static final int DEFAULT_MAX_CONNECTIONS = 16;
    // 单例
    private static final ChannelPool INSTANCE = new ChannelPool();
    // 按服务地址存储连接池
    private final Map<InetSocketAddress, CopyOnWriteArrayList<Channel>> channelPools = new ConcurrentHashMap<>();
    // 连接计数器
    private final Map<InetSocketAddress, AtomicInteger> channelCounter = new ConcurrentHashMap<>();
    // 每个地址的最大连接数
    private final Map<InetSocketAddress, Integer> maxConnectionsMap = new ConcurrentHashMap<>();
    // 连接创建锁
    private final ReentrantLock lock = new ReentrantLock();
    // 客户端启动器
    private Bootstrap bootstrap;
    // 随机数生成器，用于负载均衡
    private final Random random = new Random();

    private ChannelPool() {
    }

    public static ChannelPool getInstance() {
        return INSTANCE;
    }

    /**
     * 设置Bootstrap
     */
    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * 设置指定地址的最大连接数
     */
    public void setMaxConnections(InetSocketAddress address, int maxConnections) {
        maxConnectionsMap.put(address, maxConnections);
    }

    /**
     * 获取指定地址的最大连接数
     */
    public int getMaxConnections(InetSocketAddress address) {
        return maxConnectionsMap.getOrDefault(address, DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * 获取或创建Channel
     * 策略：
     * 1. 未达到最大连接数则创建新连接
     * 2. 达到最大连接数则随机获取一个可用连接
     *
     * @param address 服务地址
     * @return Channel对象
     */
    public Channel getOrCreateChannel(InetSocketAddress address) throws Exception {
        // 获取当前连接数
        int currentCount = channelCounter.getOrDefault(address, new AtomicInteger(0)).get();
        int maxConnections = getMaxConnections(address);

        // 如果未达到最大连接数，创建新连接
        if (currentCount < maxConnections) {
            lock.lock();
            try {
                // 双重检查连接数
                currentCount = channelCounter.getOrDefault(address, new AtomicInteger(0)).get();
                if (currentCount < maxConnections) {
                    // 创建新连接
                    if (bootstrap == null) {
                        throw new IllegalStateException("Bootstrap未设置，无法创建新连接");
                    }

                    Channel newChannel = bootstrap.connect(address).sync().channel();
                    if (newChannel != null && newChannel.isActive()) {
                        addChannel(address, newChannel);
                        log.info("创建新连接：{}, 当前连接数：{}/{}", address,
                                channelCounter.get(address).get(), maxConnections);
                        return newChannel;
                    }
                    throw new Exception("创建新连接失败");
                }
            } finally {
                lock.unlock();
            }
        }

        // 如果达到最大连接数，随机获取一个可用连接
        Channel channel = getRandomAvailableChannel(address);
        if (channel != null) {
            return channel;
        }

        // 没有可用连接，抛出异常
        throw new Exception("连接池已满且没有可用连接，请稍后重试");
    }

    /**
     * 随机获取一个可用的channel（负载均衡）
     *
     * @param address 服务地址
     * @return Channel对象，如果没有可用连接返回null
     */
    public Channel getRandomAvailableChannel(InetSocketAddress address) {
        CopyOnWriteArrayList<Channel> channels = channelPools.get(address);
        if (channels == null || channels.isEmpty()) {
            return null;
        }

        // 筛选出所有可用的连接
        List<Channel> availableChannels = channels.stream()
                .filter(ch -> ch != null && ch.isActive() && ch.isWritable())
                .collect(Collectors.toList());

        if (availableChannels.isEmpty()) {
            return null;
        }

        // 随机选择一个可用连接
        int index = random.nextInt(availableChannels.size());
        Channel selectedChannel = availableChannels.get(index);

        log.debug("随机获取到一个可用连接[{}/{}]: {}",
                index + 1, availableChannels.size(), selectedChannel);
        return selectedChannel;
    }

    /**
     * 获取可用的channel
     *
     * @param address 服务地址
     * @return Channel对象，如果没有可用连接返回null
     * @deprecated 使用 getRandomAvailableChannel 代替
     */
    @Deprecated
    public Channel getChannel(InetSocketAddress address) {
        return getRandomAvailableChannel(address);
    }

    /**
     * 添加Channel到连接池
     *
     * @param address 服务地址
     * @param channel 连接对象
     */
    public void addChannel(InetSocketAddress address, Channel channel) {
        if (channel == null) {
            return;
        }

        channelPools.computeIfAbsent(address, k -> new CopyOnWriteArrayList<>()).add(channel);
        channelCounter.computeIfAbsent(address, k -> new AtomicInteger(0)).incrementAndGet();
        log.info("添加新连接到连接池，地址: {}，当前连接数: {}", address, channelCounter.get(address).get());
    }

    /**
     * 移除不可用的Channel
     *
     * @param address 服务地址
     * @param channel 待移除的连接
     */
    public void removeChannel(InetSocketAddress address, Channel channel) {
        if (channel == null) {
            return;
        }

        CopyOnWriteArrayList<Channel> channels = channelPools.get(address);
        if (channels != null) {
            channels.remove(channel);
            channelCounter.get(address).decrementAndGet();
            log.info("从连接池移除连接，地址: {}，当前连接数: {}", address, channelCounter.get(address).get());
        }
    }

    /**
     * 关闭指定地址的所有连接
     *
     * @param address 服务地址
     */
    public void closeChannels(InetSocketAddress address) {
        CopyOnWriteArrayList<Channel> channels = channelPools.get(address);
        if (channels != null) {
            for (Channel channel : channels) {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            }
            channels.clear();
            channelCounter.get(address).set(0);
            log.info("关闭地址{}的所有连接", address);
        }
    }

    /**
     * 清理所有不可用的连接
     */
    public void cleanInvalidChannels() {
        for (Map.Entry<InetSocketAddress, CopyOnWriteArrayList<Channel>> entry : channelPools.entrySet()) {
            InetSocketAddress address = entry.getKey();
            CopyOnWriteArrayList<Channel> channels = entry.getValue();

            for (Channel channel : channels) {
                if (channel != null && (!channel.isActive() || !channel.isWritable())) {
                    removeChannel(address, channel);
                    log.info("清理不可用连接: {}", channel);
                }
            }
        }
    }

    /**
     * 连接池状态信息
     */
    public void printPoolState() {
        for (Map.Entry<InetSocketAddress, AtomicInteger> entry : channelCounter.entrySet()) {
            log.info("连接池状态 - 地址: {}, 连接数: {}, 最大连接数: {}",
                    entry.getKey(), entry.getValue().get(), getMaxConnections(entry.getKey()));
        }
    }

    /**
     * 关闭所有连接并清理资源
     */
    public void shutdown() {
        for (Map.Entry<InetSocketAddress, CopyOnWriteArrayList<Channel>> entry : channelPools.entrySet()) {
            InetSocketAddress address = entry.getKey();
            closeChannels(address);
        }
        channelPools.clear();
        channelCounter.clear();
        log.info("连接池已关闭，所有资源已清理");
    }

    /**
     * 获取连接池中特定地址的连接数
     */
    public int getConnectionCount(InetSocketAddress address) {
        AtomicInteger counter = channelCounter.get(address);
        return counter != null ? counter.get() : 0;
    }
}
