/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:54:21
 * @LastEditTime: 2025-04-24 21:46:27
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.netty.handler;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.invoker.InvokerManager;
import com.weihua.rpc.core.protocol.codec.RpcDecoder;
import com.weihua.rpc.core.protocol.codec.RpcEncoder;
import com.weihua.rpc.core.serialize.Serializer;
import com.weihua.rpc.core.serialize.SerializerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.ClientCnxn;

/**
 * Netty客户端通道初始化器
 */
@Slf4j
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final int READER_IDLE_TIME = 15; // 秒
    private static final int WRITER_IDLE_TIME = 10; // 秒

    private ClientConfig clientConfig;
    private static InvokerManager invokerManager; // 静态引用

    public static void setInvokerManager(InvokerManager invokerManager) {
        NettyClientInitializer.invokerManager = invokerManager;
    }

    public NettyClientInitializer(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public NettyClientInitializer() {

    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        try {
            // 1. 添加通道状态监控
            pipeline.addLast("channelStatus", new ChannelStatusHandler());

            // 2. 添加空闲检测
            pipeline.addLast("idleState", new IdleStateHandler(
                    clientConfig.getReaderIdleTime().toSeconds(),
                    clientConfig.getWriterIdleTime().toSeconds(),
                    clientConfig.getAllIdleTime().toSeconds(),
                    TimeUnit.SECONDS));

            // 3. 添加编解码器
            Serializer serializer = SerializerFactory.getDefaultSerializer();
            pipeline.addLast("encoder", new RpcEncoder(serializer));
            pipeline.addLast("decoder", new RpcDecoder(serializer));

            // 4. 添加业务处理器
            pipeline.addLast("clientHandler", new NettyClientHandler());

            // 5. 添加心跳处理器
            pipeline.addLast("heartbeat", new HeartBeatHandler());

            // 添加连接生命周期监听器 - 放在最前面确保先捕获事件
            pipeline.addFirst(new ConnectionLifecycleHandler());

            log.debug("客户端通道初始化完成");
        } catch (Exception e) {
            log.error("初始化客户端通道失败", e);
            throw new RuntimeException("初始化客户端通道失败", e);
        }
    }

    /**
     * 连接生命周期监听器
     */
    private static class ConnectionLifecycleHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("捕获到连接断开事件: {} [处理器ID: {}]",
                    ctx.channel().remoteAddress(), this.hashCode());
            if (invokerManager != null) {
                InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
                if (address != null) {
                    log.info("连接断开，添加到健康检查队列: {}", address);
                    invokerManager.addToHealthCheckQueue(address);
                }
            }
            ctx.fireChannelInactive(); // 传递事件
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("连接异常: {}", cause.getMessage());
            ctx.fireExceptionCaught(cause); // 传递事件
        }
    }
}
