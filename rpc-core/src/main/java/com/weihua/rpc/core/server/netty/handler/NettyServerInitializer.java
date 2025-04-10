/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:23:13
 * @LastEditTime: 2025-04-10 02:23:15
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.server.netty.handler;

import com.weihua.rpc.core.protocol.codec.RpcDecoder;
import com.weihua.rpc.core.protocol.codec.RpcEncoder;
import com.weihua.rpc.core.serialize.SerializerFactory;
import com.weihua.rpc.core.server.config.ServerConfig;
import com.weihua.rpc.core.server.provider.ServiceProvider;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Netty服务端通道初始化器
 */
@Slf4j
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final ServiceProvider serviceProvider;
    private final ServerConfig serverConfig;

    public NettyServerInitializer(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        // 创建一个默认配置
        this.serverConfig = new ServerConfig();
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        try {
            ch.pipeline().addLast("healthCheck", new HealthCheckHandler());

            // 1. 添加空闲状态处理器
            pipeline.addLast("idleState", new IdleStateHandler(
                    serverConfig.getReaderIdleTime(),
                    serverConfig.getWriterIdleTime(),
                    serverConfig.getAllIdleTime(),
                    TimeUnit.SECONDS));

            // 2. 添加编解码器
            pipeline.addLast("decoder", new RpcDecoder(SerializerFactory.getDefaultSerializer()));
            pipeline.addLast("encoder", new RpcEncoder(SerializerFactory.getDefaultSerializer()));

            // 3. 添加心跳处理器
            pipeline.addLast("heartbeat", new HeartbeatHandler());

            // 4. 添加追踪处理器
            // pipeline.addLast("trace", new TraceServerHandler());

            // 5. 添加业务处理器
            pipeline.addLast("serverHandler", new NettyServerHandler(serviceProvider));

            log.debug("服务端通道初始化完成: {}", ch.remoteAddress());

        } catch (Exception e) {
            log.error("服务端通道初始化失败", e);
            throw e;
        }
    }
}
