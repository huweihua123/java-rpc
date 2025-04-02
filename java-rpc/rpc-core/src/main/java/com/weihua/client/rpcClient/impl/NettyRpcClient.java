package com.weihua.client.rpcClient.impl;

import com.weihua.client.netty.handler.MDCChannelHandler;
import com.weihua.client.netty.nettyInitializer.NettyClientInitializer;
import com.weihua.client.rpcClient.RpcClient;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.impl.ConsulServiceCenter;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.trace.TraceContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.Map;

@Log4j2
public class NettyRpcClient implements RpcClient {
    public static final Bootstrap bootstrap;

    public static final EventLoopGroup eventLoopGroup;

    static {
        bootstrap = new Bootstrap();
        eventLoopGroup = new NioEventLoopGroup();

        bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class).handler(new NettyClientInitializer());
    }

    private ServiceCenter serviceCenter;

    public NettyRpcClient() {
//        this.serviceCenter = new ZkServiceCenter();
        this.serviceCenter = ConsulServiceCenter.getInstance();
    }

    public NettyRpcClient(ServiceCenter serviceCenter) {
        this.serviceCenter = serviceCenter;
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        Map<String, String> mdcContextMap = TraceContext.getCopy();
        long startTime = System.currentTimeMillis();

        InetSocketAddress address = serviceCenter.serviceDiscovery(request);
        log.info("服务发现完成，耗时 {}ms", System.currentTimeMillis() - startTime);

        String host = address.getHostName();
        int port = address.getPort();
        try {
            long connectStart = System.currentTimeMillis();
            ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
            Channel channel = channelFuture.channel();
            log.info("建立连接耗时 {}ms", System.currentTimeMillis() - connectStart);

            channel.attr(MDCChannelHandler.TRACE_CONTEXT_KEY).set(mdcContextMap);

            long sendStart = System.currentTimeMillis();
            channel.writeAndFlush(request);
            log.info("请求发送耗时 {}ms", System.currentTimeMillis() - sendStart);

            long waitStart = System.currentTimeMillis();
            channel.closeFuture().sync();
            log.info("等待响应耗时 {}ms", System.currentTimeMillis() - waitStart);

            long processStart = System.currentTimeMillis();
            AttributeKey<RpcResponse> key = AttributeKey.valueOf("RPCResponse");
            RpcResponse response = channel.attr(key).get();

            if (response == null) {
                log.error("服务响应为空，总耗时 {}ms", System.currentTimeMillis() - startTime);
                return RpcResponse.fail("服务响应为空");
            }

            log.info("收到响应:{}", response);

            log.debug("响应处理耗时 {}ms，总耗时 {}ms",
                    System.currentTimeMillis() - processStart,
                    System.currentTimeMillis() - startTime);

            return response;
        } catch (InterruptedException e) {
            log.error("请求被中断，发送请求失败: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("发送请求时发生异常: {}", e.getMessage(), e);
        } finally {
            //
        }

        return RpcResponse.fail("请求失败");
    }

    @Override
    public void close() {
        if (eventLoopGroup != null) {
            try {
                eventLoopGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                log.error("关闭 Netty 资源时发生异常: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
    }


}
