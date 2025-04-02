package com.weihua.server.netty.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class HeartbeatHandler extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        try {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                IdleState idleState = e.state();

                if (idleState == IdleState.READER_IDLE) {
                    log.info("超过10秒没有收到客户端心跳， channel: " + ctx.channel());
                    ctx.close();
                } else if (idleState == IdleState.WRITER_IDLE) {
                    log.info("超过20s没有写数据,channel: " + ctx.channel());
                    ctx.close();
                }
            }
        } catch (Exception e) {
            log.error("处理事件发生异常" + e);
        }
    }
}
