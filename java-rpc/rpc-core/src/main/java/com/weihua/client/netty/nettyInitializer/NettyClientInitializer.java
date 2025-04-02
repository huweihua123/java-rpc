package com.weihua.client.netty.nettyInitializer;

import com.weihua.client.netty.handler.HeartBeatHandler;
import com.weihua.client.netty.handler.MDCChannelHandler;
import com.weihua.client.netty.handler.NettyClientHandler;
import common.serializer.mySerializer.Serializer;
import common.serializer.mycoder.MyDecoder;
import common.serializer.mycoder.MyEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

@Log4j2
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        try {
            //消息格式 【长度】【消息体】，解决沾包问题
            pipeline.addLast(new MyEncoder(Serializer.getSerializerByType(1)));
            pipeline.addLast(new MyDecoder());
            pipeline.addLast(new NettyClientHandler());
            pipeline.addLast(new MDCChannelHandler());
            pipeline.addLast(new IdleStateHandler(0, 8, 0, TimeUnit.SECONDS));
            pipeline.addLast(new HeartBeatHandler());
            log.info("Netty client pipeline initialized with serializer type: {}", Serializer.getSerializerByType(1).getType());
        } catch (Exception e) {
            log.error("Error initializing Netty client pipeline", e);
            throw e;  // 重新抛出异常，确保管道初始化失败时处理正确
        }
    }
}
