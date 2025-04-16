/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:54:21
 * @LastEditTime: 2025-04-14 16:35:23
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.client.netty.handler;

import com.weihua.rpc.core.protocol.codec.RpcDecoder;
import com.weihua.rpc.core.protocol.codec.RpcEncoder;
import com.weihua.rpc.core.serialize.Serializer;
import com.weihua.rpc.core.serialize.SerializerFactory;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Netty客户端通道初始化器
 */
@Slf4j
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    // 心跳检测参数
    private static final int READER_IDLE_TIME = 0; // 读空闲时间
    private static final int WRITER_IDLE_TIME = 8; // 写空闲时间（8秒钟没有写数据就发送心跳）
    private static final int ALL_IDLE_TIME = 0; // 所有类型空闲时间

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        try {
            // 1. 添加通道状态监控
            pipeline.addLast("channelStatus", new ChannelStatusHandler());

            // 2. 添加空闲检测
            pipeline.addLast("idleState", new IdleStateHandler(
                    READER_IDLE_TIME,
                    WRITER_IDLE_TIME,
                    ALL_IDLE_TIME,
                    TimeUnit.SECONDS));

            // 3. 添加编解码器
            Serializer serializer = SerializerFactory.getDefaultSerializer();
            pipeline.addLast("encoder", new RpcEncoder(serializer));
            pipeline.addLast("decoder", new RpcDecoder(serializer));

            // 4. 添加业务处理器
            pipeline.addLast("clientHandler", new NettyClientHandler());

            // 5. 添加心跳处理器
            pipeline.addLast("heartbeat", new HeartBeatHandler());

            log.debug("客户端通道初始化完成");
        } catch (Exception e) {
            log.error("初始化客户端通道失败", e);
            throw new RuntimeException("初始化客户端通道失败", e);
        }
    }
}
