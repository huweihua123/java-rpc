/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:15:34
 * @LastEditTime: 2025-04-07 18:49:24
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.netty.handler;

import com.weihua.client.metrics.InvokerMetricsCollector;
import com.weihua.client.util.RpcFutureManager;

import common.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    // 性能指标收集器
    private final InvokerMetricsCollector metricsCollector = InvokerMetricsCollector.getInstance();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) throws Exception {
        if (response == null || response.getRequestId() == null) {
            log.warn("收到无效响应");
            return;
        }

        // 从响应中提取追踪上下文 - 实现这个注释标记的功能
        // 注意：通常在这里不需要调用extract，因为会在主线程中通过TraceCarrier.extract(response)处理
        // 如果确实需要在这里处理，可以添加一些追踪标签
        String traceId = response.getTraceId(); // 现在从响应中直接获取

        // 心跳响应处理
        if (response.isHeartBeat()) {
            log.debug("检测到心跳响应，传递给心跳处理器: {}", response.getRequestId());
            ctx.fireChannelRead(response);
            return;
        }

        // 记录性能指标
        if (response.getInterfaceName() != null) {
            long responseTime = System.currentTimeMillis() - response.getTimestamp();
            boolean success = !response.hasError();
            metricsCollector.recordRequestEnd(
                    response.getInterfaceName(),
                    null,
                    responseTime,
                    success);

            if (success) {
                log.debug("请求成功完成: {}, 接口: {}, 响应时间: {}ms, traceId: {}",
                        response.getRequestId(), response.getInterfaceName(),
                        responseTime, traceId); // 修改为使用响应中的traceId
            } else {
                log.warn("请求返回错误: {}, 接口: {}, 错误: {}, 响应时间: {}ms, traceId: {}",
                        response.getRequestId(), response.getInterfaceName(),
                        response.getError(), responseTime, traceId); // 修改为使用响应中的traceId
            }
        }

        // 完成Future
        RpcFutureManager.completeFuture(response.getRequestId(), response);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel连接建立: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel连接断开: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel发生异常: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}