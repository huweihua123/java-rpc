///*
// * @Author: weihua hu
// * @Date: 2025-04-10 02:28:56
// * @LastEditTime: 2025-04-10 02:28:58
// * @LastEditors: weihua hu
// * @Description:
// */
//package com.weihua.rpc.core.trace;
//
//import com.weihua.rpc.common.model.RpcRequest;
//import com.weihua.rpc.common.model.RpcResponse;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * 追踪信息载体
// * 负责在请求和响应之间传递追踪信息
// */
//@Slf4j
//public class TraceCarrier {
//
//    /**
//     * 将当前线程的追踪上下文注入到请求中
//     *
//     * @param request RPC请求
//     */
//    public static void inject(RpcRequest request) {
//        if (request == null) {
//            return;
//        }
//
//        TraceContext context = TraceContext.current();
//        if (context == null) {
//            // 如果当前没有追踪上下文，创建一个新的
//            context = TraceContext.newRootContext(
//                    request.getInterfaceName(),
//                    request.getMethodName());
//            log.debug("创建新的追踪上下文: traceId={}, spanId={}",
//                    context.getTraceId(), context.getSpanId());
//        }
//
//        // 注入追踪信息到请求
//        request.setTraceId(context.getTraceId());
//        request.setSpanId(context.getSpanId());
//
//        log.debug("请求注入追踪信息: traceId={}, spanId={}, 接口={}",
//                context.getTraceId(), context.getSpanId(), request.getInterfaceName());
//    }
//
//    /**
//     * 从响应中提取追踪信息到当前线程
//     *
//     * @param response RPC响应
//     */
//    public static void extract(RpcResponse response) {
//        if (response == null || response.getTraceId() == null) {
//            return;
//        }
//
//        TraceContext context = TraceContext.current();
//        String traceId = response.getTraceId();
//        String spanId = response.getSpanId();
//        String serverSpanId = response.getServerSpanId();
//
//        if (context != null && context.getTraceId().equals(traceId)) {
//            // 当前上下文与响应中的追踪ID匹配，添加服务端信息
//            context.addTag("server.spanId", serverSpanId);
//            context.addTag("response.code", String.valueOf(response.getCode()));
//
//            if (response.getCode() != 200 && response.getMessage() != null) {
//                context.addTag("error", response.getMessage());
//            }
//
//            log.debug("响应追踪信息已处理: traceId={}, 客户端spanId={}, 服务端spanId={}",
//                    traceId, spanId, serverSpanId);
//        } else {
//            log.warn("响应追踪信息不匹配: 响应traceId={}, 当前traceId={}",
//                    traceId, context != null ? context.getTraceId() : "null");
//        }
//    }
//}
