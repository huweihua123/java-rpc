/*
 * @Author: weihua hu
 * @Date: 2025-04-01 12:23:14
 * @LastEditTime: 2025-04-03 19:22:27
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
    // 此常量可能仍被其他代码引用，暂时保留
    public static final AttributeKey<Map<String, String>> TRACE_CONTEXT_KEY = AttributeKey.valueOf("TraceContext");

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // 针对RpcRequest类型的消息，从RequestTraceContextManager获取上下文
        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest) msg;
            String requestId = request.getRequestId();

            // 从RequestTraceContextManager获取追踪上下文
            Map<String, String> traceContext = RequestTraceContextManager.getTraceContext(requestId);
            if (traceContext != null) {
                TraceContext.clone(traceContext);
                log.debug("已从RequestTraceContextManager获取Trace上下文, requestId: {}", requestId);
            } else {
                log.warn("未找到请求对应的Trace上下文, requestId: {}", requestId);
            }
        } else if (msg instanceof RpcResponse) {
            // 对于响应消息，可以选择是否处理
            RpcResponse response = (RpcResponse) msg;
            log.debug("发送响应: requestId={}", response.getRequestId());
        }

        // 传递消息到下一个处理器
        ctx.write(msg, promise);
    }
}
