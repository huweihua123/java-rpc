/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:31:00
 * @LastEditTime: 2025-04-10 01:31:02
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.exception;

/**
 * RPC 自定义异常
 */
public class RpcException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private int code;

    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcException(String message, int code) {
        super(message);
        this.code = code;
    }

    public RpcException(String message, int code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public RpcException(Throwable cause) {
        super(cause);
    }
    
    public int getCode() {
        return code;
    }
}
