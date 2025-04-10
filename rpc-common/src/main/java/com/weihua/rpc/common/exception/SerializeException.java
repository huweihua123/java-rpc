/*
 * @Author: weihua hu
 * @Date: 2025-04-10 01:42:33
 * @LastEditTime: 2025-04-10 01:42:46
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.common.exception;

/**
 * 序列化异常
 */
public class SerializeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SerializeException(String message) {
        super(message);
    }

    public SerializeException(String message, Throwable cause) {
        super(message, cause);
    }

    public SerializeException(Throwable cause) {
        super(cause);
    }
}
