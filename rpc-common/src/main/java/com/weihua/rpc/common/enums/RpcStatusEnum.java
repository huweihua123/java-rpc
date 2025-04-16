/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:43:45
 * @LastEditTime: 2025-04-16 22:07:51
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.enums;

import lombok.Getter;

/**
 * RPC 状态枚举
 */
@Getter
public enum RpcStatusEnum {

    SUCCESS(200, "成功"),

    // 客户端错误 4xx
    CLIENT_ERROR(400, "客户端错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    SERVICE_NOT_FOUND(404, "服务未找到"),
    METHOD_NOT_FOUND(405, "方法不存在"),
    RATE_LIMITED(429, "请求被限流"),

    // 服务端错误 5xx
    ERROR(500, "系统错误"),
    BAD_GATEWAY(502, "网关错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),
    TIMEOUT(504, "服务调用超时");

    private final int code;
    private final String message;

    RpcStatusEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // 添加通过code获取枚举的方法
    public static RpcStatusEnum getByCode(int code) {
        for (RpcStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return ERROR; // 默认返回系统错误
    }
}
