package com.weihua.rpc.common.model;

import com.weihua.rpc.common.enums.RpcStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应类型枚举
     */
    public enum ResponseType {
        /**
         * 普通业务响应
         */
        NORMAL,

        /**
         * 心跳响应
         */
        HEARTBEAT
    }

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 状态码
     */
    private int code;

    /**
     * 状态信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 响应类型，默认为普通响应
     */
    @Builder.Default
    private ResponseType responseType = ResponseType.NORMAL;

    /**
     * 创建成功响应
     */
    public static <T> RpcResponse<T> success(String requestId, T data) {
        return RpcResponse.<T>builder()
                .requestId(requestId)
                .code(RpcStatusEnum.SUCCESS.getCode())
                .message(RpcStatusEnum.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static <T> RpcResponse<T> fail(String requestId, RpcStatusEnum rpcStatus) {
        return RpcResponse.<T>builder()
                .requestId(requestId)
                .code(rpcStatus.getCode())
                .message(rpcStatus.getMessage())
                .build();
    }

    /**
     * 创建心跳响应
     */
    public static <T> RpcResponse<T> heartBeat(String requestId) {
        return RpcResponse.<T>builder()
                .requestId(requestId)
                .code(RpcStatusEnum.SUCCESS.getCode())
                .message("pong")
                .responseType(ResponseType.HEARTBEAT)
                .build();
    }

    /**
     * 判断是否为心跳响应
     */
    public boolean isHeartBeat() {
        return ResponseType.HEARTBEAT.equals(this.responseType);
    }

    /**
     * 设置为心跳响应
     * 兼容现有代码
     */
    public void setHeartBeat(boolean isHeartBeat) {
        if (isHeartBeat) {
            this.responseType = ResponseType.HEARTBEAT;
        } else {
            this.responseType = ResponseType.NORMAL;
        }
    }
}