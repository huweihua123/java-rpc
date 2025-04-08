/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:14:21
 * @LastEditTime: 2025-04-07 17:28:04
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse implements Serializable {
    private int code; // 状态码，200表示成功，其他表示失败
    private String requestId; // 对应请求的ID
    private String interfaceName; // 添加接口名，用于监控统计
    private String message; // 响应消息，通常在失败时包含错误信息
    private Object data; // 响应数据
    private Class<?> datatype; // 数据类型
    private String error; // 错误信息，如果有的话
    private long timestamp; // 时间戳，用于计算响应时间
    private boolean heartBeat; // 是否是心跳响应
    private Map<String, String> attachments; // 附加信息，用于传递自定义数据

    // 链路追踪相关字段
    private String traceId; // 全局追踪ID
    private String spanId; // 当前调用的spanId
    private String serverSpanId; // 服务端处理的spanId

    // 响应码常量
    public static final int SUCCESS_CODE = 200;
    public static final int ERROR_CODE = 500;
    public static final int NOT_FOUND_CODE = 404;
    public static final int TIMEOUT_CODE = 408;

    /**
     * 创建成功响应
     */
    public static RpcResponse success(Object data) {
        return RpcResponse.builder().code(SUCCESS_CODE)
                .datatype(data != null ? data.getClass() : null)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带请求ID的成功响应
     */
    public static RpcResponse success(Object data, String requestId) {
        return RpcResponse.builder().code(SUCCESS_CODE)
                .requestId(requestId)
                .datatype(data != null ? data.getClass() : null)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带接口名的成功响应
     */
    public static RpcResponse success(Object data, String requestId, String interfaceName) {
        return RpcResponse.builder().code(SUCCESS_CODE)
                .requestId(requestId)
                .interfaceName(interfaceName)
                .datatype(data != null ? data.getClass() : null)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带追踪信息的成功响应
     */
    public static RpcResponse success(Object data, String requestId, String interfaceName,
            String traceId, String spanId, String serverSpanId) {
        return RpcResponse.builder().code(SUCCESS_CODE)
                .requestId(requestId)
                .interfaceName(interfaceName)
                .datatype(data != null ? data.getClass() : null)
                .data(data)
                .traceId(traceId)
                .spanId(spanId)
                .serverSpanId(serverSpanId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建通用失败响应
     */
    public static RpcResponse fail() {
        return RpcResponse.builder().code(ERROR_CODE)
                .message("服务器发送内部错误")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带错误消息的失败响应
     */
    public static RpcResponse fail(String msg) {
        return RpcResponse.builder().code(ERROR_CODE)
                .message(msg)
                .error(msg)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带请求ID的失败响应
     */
    public static RpcResponse fail(String msg, String requestId) {
        return RpcResponse.builder().requestId(requestId)
                .code(ERROR_CODE)
                .message(msg)
                .error(msg)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带接口名的失败响应
     */
    public static RpcResponse fail(String msg, String requestId, String interfaceName) {
        return RpcResponse.builder().requestId(requestId)
                .interfaceName(interfaceName)
                .code(ERROR_CODE)
                .message(msg)
                .error(msg)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带追踪信息的失败响应
     */
    public static RpcResponse fail(String msg, String requestId, String interfaceName,
            String traceId, String spanId, String serverSpanId) {
        return RpcResponse.builder().requestId(requestId)
                .interfaceName(interfaceName)
                .code(ERROR_CODE)
                .message(msg)
                .error(msg)
                .traceId(traceId)
                .spanId(spanId)
                .serverSpanId(serverSpanId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建超时响应
     */
    public static RpcResponse timeout(String requestId) {
        return RpcResponse.builder().requestId(requestId)
                .code(TIMEOUT_CODE)
                .message("请求超时")
                .error("Request timeout")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带追踪信息的超时响应
     */
    public static RpcResponse timeout(String requestId, String traceId, String spanId) {
        return RpcResponse.builder().requestId(requestId)
                .code(TIMEOUT_CODE)
                .message("请求超时")
                .error("Request timeout")
                .traceId(traceId)
                .spanId(spanId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建服务未找到响应
     */
    public static RpcResponse notFound(String serviceName, String requestId) {
        return RpcResponse.builder().requestId(requestId)
                .interfaceName(serviceName)
                .code(NOT_FOUND_CODE)
                .message("服务未找到: " + serviceName)
                .error("Service not found: " + serviceName)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建带追踪信息的服务未找到响应
     */
    public static RpcResponse notFound(String serviceName, String requestId,
            String traceId, String spanId) {
        return RpcResponse.builder().requestId(requestId)
                .interfaceName(serviceName)
                .code(NOT_FOUND_CODE)
                .message("服务未找到: " + serviceName)
                .error("Service not found: " + serviceName)
                .traceId(traceId)
                .spanId(spanId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建心跳响应
     */
    public static RpcResponse heartBeat(String requestId) {
        return RpcResponse.builder().requestId(requestId)
                .code(SUCCESS_CODE)
                .heartBeat(true)
                .message("pong")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 设置追踪信息
     */
    public RpcResponse withTrace(String traceId, String spanId, String serverSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.serverSpanId = serverSpanId;
        return this;
    }

    /**
     * 添加附加信息
     */
    public RpcResponse addAttachment(String key, String value) {
        if (this.attachments == null) {
            this.attachments = new HashMap<>();
        }
        this.attachments.put(key, value);
        return this;
    }

    /**
     * 检查响应是否存在错误
     */
    public boolean hasError() {
        return code != SUCCESS_CODE || error != null;
    }
}