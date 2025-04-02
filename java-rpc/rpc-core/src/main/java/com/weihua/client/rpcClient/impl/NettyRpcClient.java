package com.weihua.client.rpcClient.impl;

import com.weihua.client.netty.handler.MDCChannelHandler;
import com.weihua.client.netty.nettyInitializer.NettyClientInitializer;
import com.weihua.client.pool.ChannelPool;
import com.weihua.client.rpcClient.RpcClient;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.impl.ConsulServiceCenter;
import com.weihua.client.util.RpcFutureManager;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.trace.TraceContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Log4j2
public class NettyRpcClient implements RpcClient {
    // 默认配置
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 5;

    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final ChannelPool channelPool;
    private final ServiceCenter serviceCenter;

    // 是否已经启动定期清理任务
    private volatile boolean cleanTaskStarted = false;

    public NettyRpcClient() {
        this(ConsulServiceCenter.getInstance());
    }

    public NettyRpcClient(ServiceCenter serviceCenter) {
        this(serviceCenter, DEFAULT_CONNECT_TIMEOUT_MILLIS);
    }

    public NettyRpcClient(ServiceCenter serviceCenter, int connectTimeoutMillis) {
        this.serviceCenter = serviceCenter;

        // 初始化Netty组件
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new NettyClientInitializer())
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true); // 启用TCP keepalive

        // 获取连接池并设置Bootstrap
        this.channelPool = ChannelPool.getInstance();
        this.channelPool.setBootstrap(bootstrap);

        // 启动定期检查连接池中失效连接的任务
        startCleanTask();

        log.info("NettyRpcClient初始化完成，连接超时：{}ms", connectTimeoutMillis);
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        Map<String, String> mdcContextMap = TraceContext.getCopy();
        long startTime = System.currentTimeMillis();

        InetSocketAddress address = serviceCenter.serviceDiscovery(request);
        log.info("服务发现完成，耗时 {}ms", System.currentTimeMillis() - startTime);

        try {
            // 从连接池获取或创建连接（会根据最大连接数自动判断）
            long connectStart = System.currentTimeMillis();
            Channel channel = channelPool.getOrCreateChannel(address);
            log.info("获取连接耗时 {}ms，当前连接数：{}/{}",
                    System.currentTimeMillis() - connectStart,
                    channelPool.getConnectionCount(address),
                    channelPool.getMaxConnections(address));

            channel.attr(MDCChannelHandler.TRACE_CONTEXT_KEY).set(mdcContextMap);

            // 设置异步响应处理
            CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
            RpcFutureManager.putFuture(request.getRequestId(), responseFuture);

            // 异步发送请求
            long sendStart = System.currentTimeMillis();
            ChannelFuture writeFuture = channel.writeAndFlush(request);

            // 添加写操作监听器
            writeFuture.addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("请求发送成功");
                } else {
                    log.error("请求发送失败", future.cause());
                    responseFuture.completeExceptionally(future.cause());
                    // 只在请求发送失败时从连接池移除连接
                    channelPool.removeChannel(address, channel);
                }
            });

            log.info("请求发送耗时 {}ms", System.currentTimeMillis() - sendStart);

            // 异步等待响应（可以设置超时）
            long waitStart = System.currentTimeMillis();
            RpcResponse response = responseFuture.get(DEFAULT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("等待响应耗时 {}ms", System.currentTimeMillis() - waitStart);

            // 处理结果
            log.info("收到响应:{}", response);
            log.debug("总耗时 {}ms", System.currentTimeMillis() - startTime);

            return response;
        } catch (TimeoutException e) {
            log.error("请求超时: {}", e.getMessage());
            RpcFutureManager.removeFuture(request.getRequestId());
            return RpcResponse.fail("请求超时");
        } catch (InterruptedException e) {
            log.error("请求被中断: {}", e.getMessage());
            Thread.currentThread().interrupt();
            RpcFutureManager.removeFuture(request.getRequestId());
        } catch (ExecutionException e) {
            log.error("执行异常: {}", e.getCause().getMessage());
            RpcFutureManager.removeFuture(request.getRequestId());
        } catch (Exception e) {
            log.error("发送请求异常: {}", e.getMessage());
            RpcFutureManager.removeFuture(request.getRequestId());
        }

        return RpcResponse.fail("请求失败");
    }

    /**
     * 设置服务地址的最大连接数
     */
    public void setMaxConnections(InetSocketAddress address, int maxConnections) {
        channelPool.setMaxConnections(address, maxConnections);
    }

    /**
     * 启动定期清理任务
     */
    private void startCleanTask() {
        if (!cleanTaskStarted) {
            synchronized (this) {
                if (!cleanTaskStarted) {
                    eventLoopGroup.scheduleAtFixedRate(() -> {
                        try {
                            // 只检查连接状态，不主动关闭连接
                            channelPool.printPoolState();
                        } catch (Exception e) {
                            log.error("连接池定期检查任务异常", e);
                        }
                    }, 30, 30, TimeUnit.SECONDS);
                    cleanTaskStarted = true;
                    log.info("已启动连接池定期检查任务");
                }
            }
        }
    }

    /**
     * 打印连接池状态
     */
    public void printPoolState() {
        channelPool.printPoolState();
    }

    @Override
    public void close() {
        // 先关闭连接池资源
        channelPool.shutdown();

        // 再关闭事件循环组
        if (eventLoopGroup != null) {
            try {
                eventLoopGroup.shutdownGracefully().sync();
                log.info("NettyRpcClient已关闭");
            } catch (InterruptedException e) {
                log.error("关闭 Netty 资源时发生异常: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}
