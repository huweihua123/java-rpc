/*
 * @Author: weihua hu
 * @Date: 2025-03-22 14:56:21
 * @LastEditTime: 2025-04-08 16:03:44
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.server.netty.handler;

import com.weihua.server.provider.ServiceProvider;
import common.message.RequestType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private final ServiceProvider serviceProvider;
    private static final ExecutorService SERVICE_EXECUTOR = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    public NettyServerHandler(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("服务端业务处理器 - 连接激活: {}", ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("服务端业务处理器 - 连接断开: {}", ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        if (request == null) {
            log.error("接收到非法请求，RpcRequest 为空");
            return;
        }

        // 处理心跳请求
        if (request.isHeartBeat() || request.getType() == RequestType.HEARTBEAT) {
            log.debug("接收到来自客户端的心跳包: {}", request.getRequestId());
            RpcResponse heartbeatResponse = RpcResponse.success("pong");
            heartbeatResponse.setRequestId(request.getRequestId());
            heartbeatResponse.setHeartBeat(true);
            ctx.writeAndFlush(heartbeatResponse);
            return;
        }

        // 处理业务请求
        SERVICE_EXECUTOR.submit(() -> {
            RpcResponse response = null;
            boolean success = false;
            String errorMessage = null;
            String serviceName = request.getInterfaceName();
            String methodName = request.getMethodName();

            // 提取和存储追踪上下文到REQUEST_CONTEXT_MAP
            com.weihua.server.handler.TraceServerHandler.handleRequest(request);

            try {
                // 记录服务调用
                log.info("服务器收到请求: {}#{}", serviceName, methodName);
                response = handleRequest(request);

                if (response != null && !response.hasError()) {
                    success = true;
                } else if (response != null) {
                    errorMessage = "错误码: " + response.getCode() + ", 消息: " + response.getMessage();
                }
            } catch (Exception e) {
                log.error("处理请求时发生错误", e);
                errorMessage = e.getMessage();
                response = RpcResponse.fail(e.getMessage());
            } finally {
                if (response != null) {
                    // 设置请求ID，确保可以找到对应的追踪上下文
                    response.setRequestId(request.getRequestId());

                    // 处理响应追踪信息
                    com.weihua.server.handler.TraceServerHandler.handleResponse(response);

                    ChannelFuture future = ctx.writeAndFlush(response);
                    future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                    log.debug("服务端响应已发送: {}", response);
                }
            }
        });
    }

    private RpcResponse handleRequest(RpcRequest request) {
        // 原有业务处理逻辑保持不变
        Object service = serviceProvider.getService(request.getInterfaceName());
        if (service == null) {
            log.error("找不到服务实现: {}", request.getInterfaceName());
            return RpcResponse.fail("服务未实现: " + request.getInterfaceName());
        }

        try {
            Method method = service.getClass().getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(service, request.getParameters());
            return RpcResponse.success(result);
        } catch (NoSuchMethodException e) {
            log.error("找不到方法: {}#{}", request.getInterfaceName(), request.getMethodName());
            return RpcResponse.fail("找不到方法: " + request.getMethodName());
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("调用方法时发生错误", e);
            return RpcResponse.fail("调用方法失败: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("服务器处理器发生异常", cause);
        ctx.close();
    }
}