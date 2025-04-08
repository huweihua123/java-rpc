package com.weihua.client.netty.nettyInitializer;

import com.weihua.client.netty.handler.ChannelStatusHandler;
import com.weihua.client.netty.handler.HeartBeatHandler;
import com.weihua.client.netty.handler.NettyClientHandler;
import com.weihua.config.heartbeat.HeartbeatConfig;

import common.serializer.mySerializer.Serializer;
import common.serializer.mycoder.MyDecoder;
import common.serializer.mycoder.MyEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    // 心跳配置
    private final HeartbeatConfig heartbeatConfig;

    public NettyClientInitializer() {
        this.heartbeatConfig = HeartbeatConfig.getInstance();

        log.info("客户端心跳配置: 读空闲={}{}，写空闲={}{}，全空闲={}{}",
                heartbeatConfig.getReaderIdleTime(), heartbeatConfig.getTimeUnit(),
                heartbeatConfig.getWriterIdleTime(), heartbeatConfig.getTimeUnit(),
                heartbeatConfig.getAllIdleTime(), heartbeatConfig.getTimeUnit());
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        try {
            // 1. 添加通道状态监控
            pipeline.addLast("channelStatus", new ChannelStatusHandler());

            // 2. 添加空闲检测
            pipeline.addLast("idleState", new IdleStateHandler(
                    heartbeatConfig.getReaderIdleTime(),
                    heartbeatConfig.getWriterIdleTime(),
                    heartbeatConfig.getAllIdleTime(),
                    heartbeatConfig.getTimeUnit()));

            // 3. 添加编解码器
            pipeline.addLast("encoder", new MyEncoder(Serializer.getSerializerByType(1)));
            pipeline.addLast("decoder", new MyDecoder());

            // 5. 先添加业务处理器
            pipeline.addLast("clientHandler", new NettyClientHandler());

            // 6. 最后添加心跳处理器 - 注意顺序，确保心跳响应能从NettyClientHandler传递过来
            pipeline.addLast("heartbeat", new HeartBeatHandler());

            log.info("客户端通道初始化完成，序列化类型: {}, 处理器顺序已优化",
                    Serializer.getSerializerByType(1).getType());
        } catch (Exception e) {
            log.error("初始化客户端通道失败", e);
            throw e;
        }
    }
}