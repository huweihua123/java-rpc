/*
 * @Author: weihua hu
 * @Date: 2025-03-20 20:02:05
 * @LastEditTime: 2025-04-03 15:37:13
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.server.netty.handler;

import com.weihua.server.provider.ServiceProvider;
import com.weihua.server.rateLimit.RateLimit;
import com.weihua.trace.interceptor.ServerTraceInterceptor;
import common.message.RequestType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Log4j2
@AllArgsConstructor
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private ServiceProvider serviceProvider;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {

        if (request == null) {
            log.error("接收到非法请求，RpcRequest 为空");
            return;
        }

        if (request.getType() == RequestType.HEARTBEAT) {
            log.info("接收到来自客户端的心跳包");
            return;
        }

        // 正常业务处理
        if (request.getType() == RequestType.NORMAL) {
            ServerTraceInterceptor.beforeHandle();

            boolean success = false;
            String errorMessage = null;
            RpcResponse response = null;

            try {
                response = getResponse(request);

                if (response != null) {
                    response.setRequestId(request.getRequestId());
                }

                // 判断调用是否成功
                success = response != null && response.getCode() == 200;
                if (!success && response != null) {
                    errorMessage = response.getMessage();
                }
            } catch (Exception e) {
                success = false;
                errorMessage = e.getClass().getName() + ": " + e.getMessage();
                log.error("处理请求时发生异常", e);
                // 创建错误响应对象，而不是抛出异常
                response = RpcResponse.fail("服务端处理异常: " + errorMessage, request.getRequestId());
            } finally {
                // 确保始终发送响应
                if (response != null) {
                    ctx.writeAndFlush(response);
                } else {
                    ctx.writeAndFlush(RpcResponse.fail("服务端未知错误", request.getRequestId()));
                }
                // 更新链路追踪，添加成功状态和错误信息
                ServerTraceInterceptor.afterHandle(request.getMethodName(), success, errorMessage);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("处理请求时发生异常: ", cause);
        ctx.close();
    }

    private RpcResponse getResponse(RpcRequest rpcRequest) {

        // 得到服务名
        String interfaceName = rpcRequest.getInterfaceName();
        RateLimit rateLimit = serviceProvider.getRateLimitProvider().getRateLimit(interfaceName);
        if (!rateLimit.getToken()) {
            RpcResponse rpcResponse = RpcResponse.fail("服务限流，接口 " + interfaceName + " 当前无法处理请求。请稍后再试。", rpcRequest.getRequestId());
            log.warn(interfaceName + "被限流" + rpcResponse);

            return rpcResponse;
//            return RpcResponse.fail("服务限流，接口 " + interfaceName + " 当前无法处理请求。请稍后再试。",rpcRequest.getRequestId());
        }
        // 得到服务端相应服务实现类
        Object service = serviceProvider.getService(interfaceName);
        // 反射调用方法
        Method method = null;
        try {
            method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            Object invoke = method.invoke(service, rpcRequest.getParams());
            return RpcResponse.success(invoke);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("方法执行错误，接口: {}, 方法: {}", interfaceName, rpcRequest.getMethodName(), e);
            return RpcResponse.fail("方法执行错误");
        }
    }
}
