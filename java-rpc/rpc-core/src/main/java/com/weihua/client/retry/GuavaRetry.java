package com.weihua.client.retry;

import com.github.rholder.retry.*;
import com.google.common.base.Predicates;
import com.weihua.client.rpcClient.RpcClient;
import com.weihua.client.util.RpcFutureManager;
import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Log4j2
public class GuavaRetry {
    // 可配置的重试参数
    @Setter
    private int maxRetries = 2; // 默认最大重试次数

    @Setter
    private long retryIntervalMillis = 1000; // 默认重试间隔（毫秒）

    @Setter
    private Predicate<RpcResponse> retryCondition = response -> response == null || response.hasError();

    /**
     * 使用重试机制发送服务请求
     * 
     * @param request   请求对象
     * @param rpcClient RPC客户端
     * @return 响应对象
     * @throws Exception 如果所有重试都失败，则抛出异常
     */
    public RpcResponse sendServiceWithRetry(RpcRequest request, RpcClient rpcClient) throws Exception {
        // 构建重试器
        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                // 设置重试条件
                .retryIfResult(response -> retryCondition.test(response))
                .retryIfException()
                // 设置等待策略
                .withWaitStrategy(WaitStrategies.fixedWait(retryIntervalMillis, TimeUnit.MILLISECONDS))
                // 设置停止策略
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetries + 1)) // +1因为第一次不算重试
                // 设置重试监听器
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        if (attempt.hasException()) {
                            log.warn("第{}次重试失败，异常: {}", attempt.getAttemptNumber(),
                                    attempt.getExceptionCause().getMessage());
                        } else if (attempt.hasResult()) {
                            RpcResponse response = (RpcResponse) attempt.getResult();
                            log.warn("第{}次重试失败，错误码: {}, 错误信息: {}",
                                    attempt.getAttemptNumber(),
                                    response.getCode(),
                                    response.getError());
                        }
                    }
                })
                .build();

        try {
            // 执行带重试的调用
            return retryer.call(() -> {
                RpcResponse response = rpcClient.sendRequest(request);
                if (response == null) {
                    throw new RuntimeException("响应为空");
                }
                return response;
            });
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException("重试过程中发生未知错误", e);
        } catch (RetryException e) {
            log.error("重试次数已用尽，请求最终失败: {}", request.getMethodSignature());
            // 如果有最后一次尝试的结果，则返回它
            if (e.getLastFailedAttempt().hasResult()) {
                return (RpcResponse) e.getLastFailedAttempt().getResult();
            }
            throw new RuntimeException("重试次数已用尽，请求失败", e);
        }
    }
}