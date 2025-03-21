/*
 * @Author: weihua hu
 * @Date: 2025-03-20 20:02:05
 * @LastEditTime: 2025-03-21 11:01:42
 * @LastEditors: weihua hu
 * @Description:
 */
package server.netty.handler;

import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.AllArgsConstructor;
import server.provider.ServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

//
//public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
//
//    private ServiceProvider serviceProvider;
//
//    public NettyServerHandler(ServiceProvider serviceProvider) {
//        this.serviceProvider = serviceProvider;
//    }
//
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
//        System.out.println("处理tcp");
//        System.out.println(request.toString());
//
//        if ("/health".equals(request.getMethodName())) {
//
//            FullHttpResponse response = new DefaultFullHttpResponse(
//                    HttpVersion.HTTP_1_1,
//                    HttpResponseStatus.OK);
//            ctx.writeAndFlush(response);
//            return;
//        }
//
//        RpcResponse response = getResponse(request);
//        ctx.writeAndFlush(response);
//        ctx.close();
//
//    }
//
//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//        cause.printStackTrace();
//        ctx.close();
//    }
//
//    public RpcResponse getResponse(RpcRequest request) {
//        Object service = serviceProvider.getService(request.getInterfaceName());
//        Method method = null;
//        try {
//            method = Class.forName(request.getInterfaceName()).getMethod(request.getMethodName(), request.getParamTypes());
//            Object data = method.invoke(service, request.getParams());
//            return RpcResponse.success(data);
//        } catch (Exception e) {
//            return RpcResponse.fail();
//        }
//
//    }
//}
@AllArgsConstructor
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private ServiceProvider serviceProvider;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {

        System.out.println("处理tcp");
        System.out.println(request.toString());
        if ("/health".equals(request.getMethodName())) {

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK);
            ctx.writeAndFlush(response);
            return;
        }

        RpcResponse response = getResponse(request);
        ctx.writeAndFlush(response);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    private RpcResponse getResponse(RpcRequest rpcRequest) {

        // 得到服务名
        String interfaceName = rpcRequest.getInterfaceName();
        // 得到服务端相应服务实现类
        Object service = serviceProvider.getService(interfaceName);
        // 反射调用方法
        Method method = null;
        try {
            method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            Object invoke = method.invoke(service, rpcRequest.getParams());
            return RpcResponse.success(invoke);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            System.out.println("方法执行错误");
            return RpcResponse.fail();
        }
    }
}
