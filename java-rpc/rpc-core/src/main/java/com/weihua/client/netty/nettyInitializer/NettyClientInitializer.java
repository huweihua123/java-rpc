package com.weihua.client.netty.nettyInitializer;

import com.weihua.client.netty.handler.HeartBeatHandler;
import com.weihua.client.netty.handler.MDCChannelHandler;
import com.weihua.client.netty.handler.NettyClientHandler;
import common.config.ConfigurationManager;
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

    // 从配置系统获取心跳参数
    private final int readerIdleTime;
    private final int writerIdleTime;
    private final int allIdleTime;
    private final TimeUnit timeUnit;

    public NettyClientInitializer() {
        ConfigurationManager config = ConfigurationManager.getInstance();
        this.readerIdleTime = config.getInt("rpc.heartbeat.reader.idle.time", 0);
        this.writerIdleTime = config.getInt("rpc.heartbeat.writer.idle.time", 8);
        this.allIdleTime = config.getInt("rpc.heartbeat.all.idle.time", 0);

        // 获取时间单位，默认为秒
        String unitName = config.getString("rpc.heartbeat.time.unit", "SECONDS");

        // 使用局部变量，避免对final字段多次赋值
        TimeUnit unit;
        try {
            unit = TimeUnit.valueOf(unitName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的时间单位配置: {}，使用默认值: SECONDS", unitName);
            unit = TimeUnit.SECONDS;
        }
        // final字段只赋值一次
        this.timeUnit = unit;

        log.info("心跳配置: 读空闲={}{}，写空闲={}{}，全空闲={}{}",
                readerIdleTime, timeUnit, writerIdleTime, timeUnit, allIdleTime, timeUnit);
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        try {
            // 消息格式 【长度】【消息体】，解决沾包问题
            pipeline.addLast(new MyEncoder(Serializer.getSerializerByType(1)));
            pipeline.addLast(new MyDecoder());
            pipeline.addLast(new NettyClientHandler());
            pipeline.addLast(new MDCChannelHandler());

            // 使用配置的心跳参数
            pipeline.addLast(new IdleStateHandler(readerIdleTime, writerIdleTime, allIdleTime, timeUnit));
            pipeline.addLast(new HeartBeatHandler(writerIdleTime, timeUnit));

            log.info("Netty client pipeline initialized with serializer type: {}",
                    Serializer.getSerializerByType(1).getType());
        } catch (Exception e) {
            log.error("Error initializing Netty client pipeline", e);
            throw e; // 重新抛出异常，确保管道初始化失败时处理正确
        }
    }
}