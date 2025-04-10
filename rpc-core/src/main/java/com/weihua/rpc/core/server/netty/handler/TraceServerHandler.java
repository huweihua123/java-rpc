//package com.weihua.rpc.core.server.netty.handler;
//
//import com.weihua.rpc.common.model.RpcRequest;
//import com.weihua.rpc.common.model.RpcResponse;
//import com.weihua.rpc.core.trace.TraceContext;
//import com.weihua.rpc.core.trace.TraceIdGenerator;
//import io.netty.channel.ChannelDuplexHandler;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.ChannelPromise;
//import lombok.extern.slf4j.Slf4j;
//
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * 服务端追踪处理器
// * 用于处理请求和响应的追踪信息
// */
//@Slf4j
//public class TraceServerHandler extends ChannelDuplexHandler {
//
//    // 使用ConcurrentHashMap存储requestId到追踪上下文的映射
//    private static final ConcurrentHashMap<String, TraceContext> REQUEST_CONTEXT_MAP = new ConcurrentHashMap<>();
//
//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//        if (msg instanceof RpcRequest) {
//            RpcRequest request = (RpcRequest) msg;
//
//            // 处理请求中的追踪信息，创建服务端span
//            TraceContext context = handleRequest(request);
//
//            log.debug("服务端接收请求: traceId={}, spanId={}, 服务={}, 方法={}",
//                    context.getTraceId(), context.getSpanId(),
//                    context.getServiceName(), context.getMethodName());
//        }
//
//        // 继续处理请求
//        ctx.fireChannelRead(msg);
//    }
//
//    @Override
//    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
//        if (msg instanceof RpcResponse) {
//            RpcResponse response = (RpcResponse) msg;
//
//            // 处理响应，添加追踪信息
//            handleResponse(response);
//
//            log.debug("服务端发送响应: traceId={}, 状态码={}",
//                    response.getTraceId(), response.getCode());
//        }
//
//        // 继续处理响应
//        ctx.write(msg, promise);
//    }
//
//    /**
//     * 处理请求，创建追踪上下文
//     */
//    public static TraceContext handleRequest(RpcRequest request) {
//        TraceContext context;
//
//        // 从请求中提取追踪信息
//        if (request.getTraceId() == null || request.getTraceId().isEmpty()) {
//            // 创建新的根上下文
//            context = TraceContext.newRootContext(
//                    request.getInterfaceName(),
//                    request.getMethodName());
//        } else {
//            // 使用请求中的追踪信息创建上下文
//            context = new TraceContext();
//            context.setTraceId(request.getTraceId());
//            context.setParentSpanId(request.getSpanId());
//            context.setSpanId(TraceIdGenerator.generateSpanId());
//            context.setServiceName(request.getInterfaceName());
//            context.setMethodName(request.getMethodName());
//        }
//
//        // 添加服务端特有标签
//        context.addTag("server.timestamp", String.valueOf(System.currentTimeMillis()));
//        context.addTag("server.thread", Thread.currentThread().getName());
//        context.addTag("type", "SERVER");
//
//        // 存储到映射表中
//        REQUEST_CONTEXT_MAP.put(request.getRequestId(), context);
//
//        return context;
//    }
//
//    /**
//     * 处理响应，注入追踪信息
//     */
//    public static void handleResponse(RpcResponse response) {
//        try {
//            // 获取与请求ID绑定的上下文
//            String requestId = response.getRequestId();
//            TraceContext context = requestId != null ? REQUEST_CONTEXT_MAP.get(requestId) : null;
//
//            if (context == null) {
//                log.warn("未找到请求ID对应的追踪上下文: {}", requestId);
//                return;
//            }
//
//            // 记录服务端处理的耗时和其他追踪信息
//            long duration = System.currentTimeMillis() - context.getStartTime();
//            context.addTag("server.duration_ms", String.valueOf(duration));
//            boolean success = response.getCode() == 200;
//            context.addTag("success", String.valueOf(success));
//
//            if (!success) {
//                context.addTag("error", response.getMessage());
//            }
//
//            // 注入追踪信息到响应
//            response.setTraceId(context.getTraceId());
//            response.setSpanId(context.getParentSpanId());
//            response.setServerSpanId(context.getSpanId());
//
//            // 完成span并上报
//            context.finish();
//
//            log.debug("服务端返回响应: traceId={}, spanId={}, 处理耗时={}ms",
//                    context.getTraceId(), context.getSpanId(), duration);
//
//            // 清除请求ID对应的上下文
//            REQUEST_CONTEXT_MAP.remove(requestId);
//
//        } catch (Exception e) {
//            log.warn("处理响应追踪信息失败", e);
//        }
//    }
//
//    /**
//     * 清理超时的追踪上下文，避免内存泄漏
//     */
//    public static void cleanupStaleContexts(long timeoutMs) {
//        long currentTime = System.currentTimeMillis();
//        REQUEST_CONTEXT_MAP.forEach((requestId, context) -> {
//            if (currentTime - context.getStartTime() > timeoutMs) {
//                log.warn("清理超时的追踪上下文: requestId={}, traceId={}, 已过时 {}ms",
//                        requestId, context.getTraceId(), currentTime - context.getStartTime());
//                REQUEST_CONTEXT_MAP.remove(requestId);
//            }
//        });
//    }
//
//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//        log.error("链路追踪处理异常", cause);
//        ctx.fireExceptionCaught(cause);
//    }
//}
