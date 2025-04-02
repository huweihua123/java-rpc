package com.weihua.client.netty.handler;

import common.trace.TraceContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

@Log4j2
public class MDCChannelHandler extends ChannelOutboundHandlerAdapter {
    public static final AttributeKey<Map<String, String>> TRACE_CONTEXT_KEY = AttributeKey.valueOf("TraceContext");
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        Map<String, String> traceContext = ctx.channel().attr(TRACE_CONTEXT_KEY).get();
        if (traceContext != null) {
            TraceContext.clone(traceContext);
            log.info("已绑定Trace上下文:{}", traceContext);
        } else {
            log.error("Trace上下文未设置");
        }
        ctx.write(msg, promise);
    }
}
