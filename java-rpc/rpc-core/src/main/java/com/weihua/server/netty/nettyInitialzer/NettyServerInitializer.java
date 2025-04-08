package com.weihua.server.netty.nettyInitialzer;

import com.weihua.config.heartbeat.HeartbeatConfig;
import com.weihua.server.netty.handler.HeartbeatHandler;
import com.weihua.server.netty.handler.NettyServerHandler;
import com.weihua.server.netty.handler.NettyTraceHandler;
import com.weihua.server.provider.ServiceProvider;
import common.serializer.mySerializer.Serializer;
import common.serializer.mycoder.MyDecoder;
import common.serializer.mycoder.MyEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
    private final ServiceProvider serviceProvider;
    private final HeartbeatConfig heartbeatConfig;

    public NettyServerInitializer(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        this.heartbeatConfig = HeartbeatConfig.getInstance();
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // 1. 空闲状态检测
        pipeline.addLast("idleState", new IdleStateHandler(
                heartbeatConfig.getReaderIdleTime(),
                heartbeatConfig.getWriterIdleTime(),
                heartbeatConfig.getAllIdleTime(),
                heartbeatConfig.getTimeUnit()));

        // 2. 编解码器 - 先解码(入站)后编码(出站)
        pipeline.addLast("decoder", new MyDecoder());
        pipeline.addLast("encoder", new MyEncoder(Serializer.getSerializerByType(1)));

        // 4. 最后是心跳处理器
        pipeline.addLast("heartbeat", new HeartbeatHandler());

        // 添加链路追踪Handler (注意: 放在业务Handler之前)
        pipeline.addLast(new NettyTraceHandler());

        // 3. 业务处理器
        pipeline.addLast("serverHandler", new NettyServerHandler(serviceProvider));

        log.info("服务端通道初始化完成，读空闲超时: {}{}，写空闲超时: {}{}，处理器顺序已优化",
                heartbeatConfig.getReaderIdleTime(), heartbeatConfig.getTimeUnit(),
                heartbeatConfig.getWriterIdleTime(), heartbeatConfig.getTimeUnit());
    }
}