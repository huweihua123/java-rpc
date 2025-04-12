/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:30:48
 * @LastEditTime: 2025-04-10 15:37:40
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 请求对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求类型枚举
     */
    public enum RequestType {
        /**
         * 普通业务请求
         */
        NORMAL,

        /**
         * 心跳请求
         */
        HEARTBEAT
    }

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 服务接口名
     */
    private String interfaceName;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 参数类型数组
     */
    private Class<?>[] parameterTypes;

    /**
     * 参数数组
     */
    private Object[] parameters;

    /**
     * 版本号
     */
    private String version;

    /**
     * 分组
     */
    private String group;

    /**
     * 请求类型，默认为普通请求
     */
    @Builder.Default
    private RequestType requestType = RequestType.NORMAL;

    /**
     * 创建心跳请求
     */
    /**
     * 创建心跳请求
     */
    public static RpcRequest heartBeat() {
        // 使用更短的心跳ID格式，以区分普通请求
        String heartbeatId = "heartbeat-" + System.currentTimeMillis();
        return RpcRequest.builder()
                .requestType(RequestType.HEARTBEAT) // 修改: type -> requestType
                .requestId(heartbeatId)
                .build();
    }

    /**
     * 判断请求是否为心跳请求
     */
    public boolean isHeartBeat() {
        return RequestType.HEARTBEAT.equals(this.requestType);
    }

    /**
     * 获取方法签名
     * 格式：接口名#方法名(参数类型列表)
     */
    public String getMethodSignature() {
        if (interfaceName == null || methodName == null) {
            return null;
        }

        StringBuilder signature = new StringBuilder();
        signature.append(interfaceName).append("#").append(methodName).append("(");

        if (parameterTypes != null && parameterTypes.length > 0) { // 修改: paramTypes -> parameterTypes
            for (int i = 0; i < parameterTypes.length; i++) { // 修改: paramTypes -> parameterTypes
                if (i > 0) {
                    signature.append(",");
                }
                signature.append(parameterTypes[i].getSimpleName());
            }
        }

        signature.append(")");
        return signature.toString();
    }

}