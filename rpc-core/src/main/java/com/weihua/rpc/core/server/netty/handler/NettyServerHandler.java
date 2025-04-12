package com.weihua.rpc.core.server.netty.handler;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.core.server.provider.ServiceProvider;
import com.weihua.rpc.core.server.ratelimit.RateLimit;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Netty服务端业务处理器
 */
@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final ExecutorService SERVICE_EXECUTOR;

    static {
        // 创建业务处理线程池
        int coreSize = Runtime.getRuntime().availableProcessors() * 2;
        int maxSize = coreSize * 4;
        int queueSize = 10000;

        SERVICE_EXECUTOR = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> {
                    Thread t = new Thread(r, "rpc-service-handler");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，由调用线程执行
        );

        log.info("业务处理线程池已创建: 核心大小={}, 最大大小={}, 队列大小={}",
                coreSize, maxSize, queueSize);
    }

    private final ServiceProvider serviceProvider;

    public NettyServerHandler(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("客户端已连接: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("客户端已断开: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        // 检查请求是否有效
        if (request == null) {
            log.error("接收到无效请求");
            return;
        }

        // 处理心跳请求
        if (request.isHeartBeat()) {
            handleHeartbeat(ctx, request);
            return;
        }

        // 异步处理业务请求
        SERVICE_EXECUTOR.submit(() -> handleBusinessRequest(ctx, request));
    }

    /**
     * 处理心跳请求
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, RpcRequest request) {
        log.debug("接收到来自客户端的心跳包: {}", request.getRequestId());

        // 创建心跳响应
        RpcResponse response = RpcResponse.builder()
                .requestId(request.getRequestId())
                .code(200)
                .message("pong")
                .build();
        response.setHeartBeat(true);

        ctx.writeAndFlush(response);
    }

    /**
     * 处理业务请求
     */
    private void handleBusinessRequest(ChannelHandlerContext ctx, RpcRequest request) {
        RpcResponse response = null;
        boolean success = false;
        String serviceName = request.getInterfaceName();
        String methodName = request.getMethodName();

        log.info("处理服务请求: {}#{}", serviceName, methodName);

        try {
            // 记录请求开始时间
            long startTime = System.currentTimeMillis();

            // 调用服务处理请求
            response = invokeService(request);

            // 记录处理耗时
            long costTime = System.currentTimeMillis() - startTime;

            // 检查调用结果
            if (response != null && response.getCode() == 200) {
                success = true;
                log.info("请求处理成功: {}#{}, 耗时: {}ms", serviceName, methodName, costTime);
            } else if (response != null) {
                log.warn("请求处理失败: {}#{}, 错误: {}, 耗时: {}ms",
                        serviceName, methodName, response.getMessage(), costTime);
            }

        } catch (Exception e) {
            log.error("处理请求时发生异常: {}#{}", serviceName, methodName, e);

            // 创建异常响应
            response = RpcResponse.builder()
                    .code(500)
                    .message("服务处理异常: " + e.getMessage())
                    .build();
        } finally {
            if (response != null) {
                // 设置请求ID
                response.setRequestId(request.getRequestId());

                // 发送响应
                ctx.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        log.error("发送响应失败", future.cause());
                    }
                });
            }
        }
    }

    /**
     * 调用服务处理请求
     */
    private RpcResponse invokeService(RpcRequest request) {
        String interfaceName = request.getInterfaceName();

        // 限流检查
        RateLimit rateLimit = serviceProvider.getRateLimitProvider().getRateLimit(interfaceName);
        if (!rateLimit.allowRequest()) {
            log.warn("接口 {} 触发限流，请求被拒绝", interfaceName);
            return RpcResponse.builder()
                    .code(429) // Too Many Requests
                    .message("服务繁忙，请稍后重试")
                    .build();
        }

        // 获取服务实例
        Object serviceInstance = serviceProvider.getService(interfaceName);
        if (serviceInstance == null) {
            log.error("找不到服务实现: {}", interfaceName);
            return RpcResponse.builder()
                    .code(404)
                    .message("服务未实现: " + interfaceName)
                    .build();
        }

        try {
            // 获取方法
            Method method = serviceInstance.getClass().getMethod(
                    request.getMethodName(),
                    request.getParameterTypes());

            // 反射调用方法
            Object result = method.invoke(serviceInstance, request.getParameters());

            // 返回成功结果
            return RpcResponse.builder()
                    .code(200)
                    .message("OK")
                    .data(result)
                    .build();

        } catch (NoSuchMethodException e) {
            log.error("找不到方法: {}#{}", interfaceName, request.getMethodName(), e);
            return RpcResponse.builder()
                    .code(404)
                    .message("找不到方法: " + request.getMethodName())
                    .build();
        } catch (IllegalAccessException e) {
            log.error("方法访问权限不足", e);
            return RpcResponse.builder()
                    .code(403)
                    .message("方法访问权限不足: " + e.getMessage())
                    .build();
        } catch (InvocationTargetException e) {
            // 获取目标异常
            Throwable targetException = e.getTargetException();
            log.error("方法调用异常", targetException);
            return RpcResponse.builder()
                    .code(500)
                    .message("调用方法失败: " + targetException.getMessage())
                    .build();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("服务处理器异常", cause);
        ctx.close();
    }
}
