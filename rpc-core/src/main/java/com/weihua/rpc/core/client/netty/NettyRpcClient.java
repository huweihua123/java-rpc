package com.weihua.rpc.core.client.netty;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.invoker.Invoker;
import com.weihua.rpc.core.client.invoker.RpcFutureManager;
import com.weihua.rpc.core.client.netty.handler.NettyClientInitializer;
import com.weihua.rpc.core.client.registry.ServiceDiscovery;
import com.weihua.rpc.core.client.registry.balance.LoadBalance;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty RPC 客户端
 * 负责处理与服务端的通信
 */
@Slf4j
public class NettyRpcClient {

    private final ClientConfig clientConfig;
    private final ServiceDiscovery serviceCenter;
    private final LoadBalance loadBalance;

    // 网络组件
    @Getter
    private Bootstrap bootstrap;
    private EventLoopGroup eventLoopGroup;

    /**
     * 构造函数，接收所需依赖
     *
     * @param clientConfig  客户端配置
     * @param serviceCenter 服务发现中心
     * @param loadBalance   负载均衡策略
     */
    public NettyRpcClient(ClientConfig clientConfig, ServiceDiscovery serviceCenter, LoadBalance loadBalance) {
        this.clientConfig = clientConfig;
        this.serviceCenter = serviceCenter;
        this.loadBalance = loadBalance;
        init();
    }

    /**
     * 初始化Netty组件
     */
    public void init() {
        // 初始化Netty组件
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new NettyClientInitializer(this.clientConfig))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) clientConfig.getConnectTimeout().toMillis())
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .option(io.netty.channel.ChannelOption.TCP_NODELAY, true);

        log.info("NettyRpcClient初始化完成，连接超时: {}ms, 请求超时: {}s",
                clientConfig.getConnectTimeout(), clientConfig.getRequestTimeout());
    }

    /**
     * 发送RPC请求
     *
     * @param request 请求对象
     * @return 响应对象
     */
    public RpcResponse sendRequest(RpcRequest request) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String serviceName = request.getInterfaceName();

        try {
            // 基于Invoker的服务发现和负载均衡
            List<Invoker> invokers = serviceCenter.discoverInvokers(request);

            if (invokers == null || invokers.isEmpty()) {
                log.error("未找到服务提供者: {}", serviceName);
                return createFailResponse(request.getRequestId(), "未找到可用的服务提供者: " + serviceName);
            }

            // 使用负载均衡策略选择Invoker
            Invoker selectedInvoker = loadBalance.select(invokers, request);
            if (selectedInvoker == null) {
                log.error("负载均衡选择失败，服务: {}", serviceName);
                return createFailResponse(request.getRequestId(), "负载均衡选择失败: " + serviceName);
            }

            // 记录所选择的Invoker信息
            log.debug("负载均衡选择Invoker: {}, 服务: {}, 地址: {}, 活跃请求数: {}",
                    selectedInvoker.getId(),
                    serviceName,
                    selectedInvoker.getAddress(),
                    selectedInvoker.getActiveCount());

            // 发送请求并等待结果
            try {
                RpcResponse response = selectedInvoker.invoke(request)
                        .get(clientConfig.getRequestTimeout().toSeconds(), TimeUnit.SECONDS);

                // 请求成功
                success = (response != null && response.getCode() == 200);
                return response;
            } catch (Exception e) {
                log.error("请求执行异常: {}", e.getMessage(), e);
                RpcFutureManager.completeExceptionally(request.getRequestId(), e);
                return createFailResponse(request.getRequestId(), "请求执行异常: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("发送请求异常: {}, 服务: {}", e.getMessage(), serviceName, e);
            return createFailResponse(request.getRequestId(), "发送请求异常: " + e.getMessage());
        } finally {
            // 记录请求结束
            long responseTime = System.currentTimeMillis() - startTime;
            log.debug("请求完成: {}, 耗时: {}ms, 成功: {}", serviceName, responseTime, success);
        }
    }

    /**
     * 创建失败响应
     */
    private RpcResponse createFailResponse(String requestId, String message) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setCode(500);
        response.setMessage(message);
        return response;
    }

    /**
     * 释放资源
     */
    public void close() {
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