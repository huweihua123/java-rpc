package com.weihua.rpc.common.exception;

/**
 * 限流异常，用于限流时拒绝请求场景
 */
public class RateLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}