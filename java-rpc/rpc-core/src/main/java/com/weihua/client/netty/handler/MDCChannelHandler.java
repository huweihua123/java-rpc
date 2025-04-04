/*
 * @Author: weihua hu
 * @Date: 2025-04-01 12:23:14
 * @LastEditTime: 2025-04-04 20:43:15
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.netty.handler;

import common.message.RpcRequest;
import common.message.RpcResponse;
import common.trace.TraceContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

import com.weihua.trace.RequestTraceContextManager;

@Log4j2
public class MDCChannelHandler extends ChannelOutboundHandlerAdapter {
    public static final AttributeKey<Map<String, String>> TRACE_CONTEXT_KEY = AttributeKey.valueOf("TraceContext");

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest) msg;
            String requestId = request.getRequestId();

            // 心跳请求特殊处理
            if (request.isHeartBeat()) {
                log.debug("跳过为心跳请求设置追踪上下文, requestId: {}", requestId);
            } else {
                // 处理正常业务请求
                if (requestId != null) {
                    Map<String, String> traceContext = RequestTraceContextManager.getTraceContext(requestId);
                    if (traceContext != null && !traceContext.isEmpty()) {
                        TraceContext.clone(traceContext);
                        log.debug("已设置请求追踪上下文, requestId: {}", requestId);
                    }
                } else {
                    log.warn("业务请求的requestId为null");
                }
            }
        } else if (msg instanceof RpcResponse) {
            RpcResponse response = (RpcResponse) msg;
            log.debug("发送响应: requestId={}", response.getRequestId());
        }

        // 传递消息到下一个处理器
        ctx.write(msg, promise);
    }
}