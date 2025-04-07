package com.weihua.client.rpcClient.impl;

import com.weihua.client.invoker.Invoker;
import com.weihua.client.netty.nettyInitializer.NettyClientInitializer;
import com.weihua.client.pool.InvokerManager;
import com.weihua.client.rpcClient.RpcClient;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.balance.LoadBalance;
import com.weihua.client.serverCenter.handler.ServiceAddressChangeHandler;
import com.weihua.client.util.RpcFutureManager;
import com.weihua.trace.TraceCarrier; // 替换为新的TraceCarrier
import common.config.ConfigurationManager;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.spi.ExtensionLoader;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Log4j2
public class NettyRpcClient implements RpcClient {
    // 配置相关
    private final int connectTimeoutMillis;
    private final int requestTimeoutSeconds;
    private final int maxConnectionsPerAddress;
    private final int initConnectionsPerAddress;

    // 网络组件
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final InvokerManager invokerManager;
    private final ServiceCenter serviceCenter;

    // 负载均衡器
    private final LoadBalance loadBalance;

    // 跟踪已订阅的服务
    private final Set<String> subscribedServices = ConcurrentHashMap.newKeySet();
    // 地址变更处理器
    private final ServiceAddressChangeHandler addressChangeHandler;

    /**
     * 使用SPI加载默认ServiceCenter实现的构造函数
     */
    public NettyRpcClient() {
        // 使用SPI机制加载服务中心实现
        this(ExtensionLoader.getExtensionLoader(ServiceCenter.class).getDefaultExtension());
    }

    /**
     * 使用指定ServiceCenter的构造函数
     */
    public NettyRpcClient(ServiceCenter serviceCenter) {
        // 加载配置
        ConfigurationManager configManager = ConfigurationManager.getInstance();
        this.connectTimeoutMillis = configManager.getInt("rpc.client.connect.timeout", 3000);
        this.requestTimeoutSeconds = configManager.getInt("rpc.client.request.timeout", 5);
        this.maxConnectionsPerAddress = configManager.getInt("rpc.client.connections.max", 4);
        this.initConnectionsPerAddress = configManager.getInt("rpc.client.connections.init", 1);

        // 加载负载均衡策略
        String loadBalanceType = configManager.getString("rpc.client.loadbalance", "consistentHash");
        this.loadBalance = ExtensionLoader.getExtensionLoader(LoadBalance.class)
                .getExtension(loadBalanceType);
        log.info("使用负载均衡策略: {}", loadBalanceType);

        this.serviceCenter = serviceCenter;

        // 初始化Netty组件
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new NettyClientInitializer())
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .option(io.netty.channel.ChannelOption.TCP_NODELAY, true);

        // 初始化连接管理器
        this.invokerManager = InvokerManager.getInstance();
        this.invokerManager.setBootstrap(bootstrap);
        this.invokerManager.setDefaultTimeout(requestTimeoutSeconds);

        // 启动连接管理器
        this.invokerManager.start();

        this.addressChangeHandler = ServiceAddressChangeHandler.getInstance();

        log.info("NettyRpcClient初始化完成，连接超时: {}ms, 请求超时: {}s, 最大连接数: {}, 初始连接数: {}",
                connectTimeoutMillis, requestTimeoutSeconds, maxConnectionsPerAddress, initConnectionsPerAddress);
    }

    /**
     * 使用指定服务中心类型的构造函数
     *
     * @param serviceCenterType 服务中心类型名称
     */
    public NettyRpcClient(String serviceCenterType) {
        this(ExtensionLoader.getExtensionLoader(ServiceCenter.class).getExtension(serviceCenterType));
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        try {
            // 将当前线程的追踪上下文注入到请求中
            TraceCarrier.inject(request);

            // 确保已为该服务订阅地址变更
            ensureServiceSubscribed(request.getInterfaceName());

            // 尝试基于Invoker的服务发现和负载均衡
            try {
                List<Invoker> invokers = serviceCenter.discoverInvokers(request);

                if (invokers != null && !invokers.isEmpty()) {
                    // 使用负载均衡策略选择Invoker
                    Invoker selectedInvoker = loadBalance.select(invokers, request);

                    if (selectedInvoker != null) {
                        log.debug("发送请求到服务: {}, 使用负载均衡选择的Invoker: {}",
                                request.getInterfaceName(), selectedInvoker.getAddress());

                        // 发送请求并等待结果
                        CompletableFuture<RpcResponse> responseFuture = selectedInvoker.invoke(request);
                        RpcResponse response = responseFuture.get(requestTimeoutSeconds, TimeUnit.SECONDS);

                        // 提取响应中的追踪上下文
                        if (response != null) {
                            TraceCarrier.extract(request);
                        }

                        return response;
                    }
                }
            } catch (UnsupportedOperationException e) {
                // 服务中心不支持基于Invoker的发现，使用旧方式
                log.debug("服务中心不支持基于Invoker的服务发现，使用基于地址的发现");
            }

            // 从注册中心发现服务地址（基于地址的旧方式）
            InetSocketAddress serviceAddress = serviceCenter.serviceDiscovery(request);
            if (serviceAddress == null) {
                log.error("未找到服务: {}", request.getInterfaceName());
                return RpcResponse.fail("未找到服务: " + request.getInterfaceName());
            }

            // 设置地址最大连接数和初始连接数
            if (maxConnectionsPerAddress > 0) {
                invokerManager.setMaxConnections(
                        serviceAddress.getHostString() + ":" + serviceAddress.getPort(),
                        maxConnectionsPerAddress);
            }

            if (initConnectionsPerAddress > 0) {
                invokerManager.setInitConnections(
                        serviceAddress.getHostString() + ":" + serviceAddress.getPort(),
                        initConnectionsPerAddress);
            }

            // 获取Invoker发送请求
            log.debug("发送请求到服务: {}, 地址: {}:{}",
                    request.getInterfaceName(), serviceAddress.getHostString(), serviceAddress.getPort());

            // 获取活跃Invoker - 直接使用InetSocketAddress
            Invoker invoker = invokerManager.getInvoker(serviceAddress);
            if (invoker == null) {
                log.error("无法获取可用的Invoker, 地址: {}:{}",
                        serviceAddress.getHostString(), serviceAddress.getPort());
                return RpcResponse.fail("无法连接到服务: " +
                        serviceAddress.getHostString() + ":" + serviceAddress.getPort());
            }

            // 发送请求并等待结果
            CompletableFuture<RpcResponse> responseFuture = invoker.invoke(request);
            RpcResponse response = responseFuture.get(requestTimeoutSeconds, TimeUnit.SECONDS);

            // 提取响应中的追踪上下文
            if (response != null) {
                TraceCarrier.extract(response); // 这里修正从请求提取为从响应提取
            }

            return response;
        } catch (TimeoutException e) {
            log.error("请求超时: {}", e.getMessage());
            RpcFutureManager.removeFuture(request.getRequestId());
            return RpcResponse.fail("请求超时");
        } catch (InterruptedException e) {
            log.error("请求被中断: {}", e.getMessage());
            Thread.currentThread().interrupt();
            RpcFutureManager.removeFuture(request.getRequestId());
            return RpcResponse.fail("请求被中断");
        } catch (ExecutionException e) {
            log.error("执行异常: {}", e.getCause().getMessage());
            RpcFutureManager.removeFuture(request.getRequestId());
            return RpcResponse.fail("执行异常: " + e.getCause().getMessage());
        } catch (Exception e) {
            log.error("发送请求异常: {}", e.getMessage());
            RpcFutureManager.removeFuture(request.getRequestId());
            return RpcResponse.fail("发送请求异常: " + e.getMessage());
        }
    }

    /**
     * 确保服务已订阅地址变更事件
     */
    private void ensureServiceSubscribed(String serviceName) {
        // 保持原有代码不变
        if (!subscribedServices.contains(serviceName)) {
            synchronized (subscribedServices) {
                if (!subscribedServices.contains(serviceName)) {
                    try {
                        // 创建监听器并订阅
                        serviceCenter.subscribeAddressChange(serviceName,
                                addressChangeHandler.createAddressChangeListener(serviceName));
                        subscribedServices.add(serviceName);
                        log.info("已为服务 {} 订阅地址变更事件", serviceName);
                    } catch (Exception e) {
                        log.error("订阅服务地址变更失败: {}", serviceName, e);
                    }
                }
            }
        }
    }

    /**
     * 打印连接池状态
     */
    public void printPoolState() {
        invokerManager.printState();
    }

    @Override
    public void close() {
        // 保持原有关闭逻辑不变
        // 取消所有服务的订阅
        for (String serviceName : subscribedServices) {
            try {
                serviceCenter.unsubscribeAddressChange(serviceName,
                        addressChangeHandler.createAddressChangeListener(serviceName));
            } catch (Exception e) {
                log.warn("取消服务地址变更订阅失败: {}", serviceName, e);
            }
        }
        subscribedServices.clear();

        // 关闭连接管理器资源
        if (invokerManager != null) {
            invokerManager.shutdown();
        }

        // 关闭RPC Future管理器
        RpcFutureManager.shutdown();

        // 关闭Netty资源
        if (eventLoopGroup != null) {
            try {
                eventLoopGroup.shutdownGracefully().sync();
                log.info("NettyRpcClient已关闭");
            } catch (InterruptedException e) {
                log.error("关闭Netty资源时发生异常: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}