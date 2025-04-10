package com.weihua.rpc.core.server.netty;

import com.weihua.rpc.core.server.RpcServer;
import com.weihua.rpc.core.server.config.ServerConfig;
import com.weihua.rpc.core.server.netty.handler.NettyServerInitializer;
import com.weihua.rpc.core.server.provider.ServiceProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于Netty的RPC服务器实现
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rpc.mode", havingValue = "server", matchIfMissing = false)
public class NettyRpcServer implements RpcServer {

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private ServiceProvider serviceProvider;

    // netty服务器组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    // 服务器状态
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void start() throws Exception {
        // 如果已经启动，直接返回
        if (!running.compareAndSet(false, true)) {
            log.warn("RPC服务器已经启动，不能重复启动");
            return;
        }

        log.info("正在启动RPC服务器...");

        // 创建事件循环线程组
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(serverConfig.getIoThreads());

        try {
            // 创建服务器启动器
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new NettyServerInitializer(serviceProvider))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            // 绑定端口并启动服务器
            InetSocketAddress address = new InetSocketAddress(
                    serverConfig.getHost(),
                    serverConfig.getPort());
            channelFuture = bootstrap.bind(address).sync();

            log.info("RPC服务器启动成功，监听地址: {}:{}",
                    serverConfig.getHost(), serverConfig.getPort());

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        } catch (Exception e) {
            log.error("启动RPC服务器失败", e);
            running.set(false);
            stop();
            throw e;
        }
    }

    @Override
    @PreDestroy
    public void stop() {
        // 如果服务器未运行，直接返回
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log.info("正在关闭RPC服务器...");

        try {
            // 关闭服务器通道
            if (channelFuture != null) {
                channelFuture.channel().close().sync();
            }

            // 关闭线程组
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }

            log.info("RPC服务器已关闭");
        } catch (Exception e) {
            log.error("关闭RPC服务器时发生异常", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
