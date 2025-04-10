/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:43:45
 * @LastEditTime: 2025-04-10 01:51:24
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
    ERROR(500, "系统错误"),
    CLIENT_ERROR(400, "客户端错误"),
    SERVICE_NOT_FOUND(404, "服务未找到"),
    SERVICE_INVOKING_ERROR(503, "服务调用错误");

    private final int code;
    private final String message;

    RpcStatusEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
