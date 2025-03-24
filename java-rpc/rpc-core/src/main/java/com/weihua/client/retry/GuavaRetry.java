package com.weihua.client.retry;

import com.github.rholder.retry.*;
import com.weihua.client.rpcClient.RpcClient;
import common.message.RpcRequest;
import common.message.RpcResponse;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GuavaRetry {
    private RpcClient rpcClient;
    private static final int MAX_ATTEMPTS = 3;
    private static final long MAX_RETRY_TIME = 10;

    public RpcResponse sendServiceWithRetry(RpcRequest request, RpcClient rpcClient) throws InterruptedException {
        StopStrategy attemptsStrategy = StopStrategies.stopAfterAttempt(MAX_ATTEMPTS);
        StopStrategy timeStrategy = StopStrategies.stopAfterDelay(MAX_RETRY_TIME, TimeUnit.SECONDS);

        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                .retryIfException()
                .retryIfResult(response -> response == null)
                // 使用任意一个停止策略
                .withStopStrategy(attempt ->
                        attemptsStrategy.shouldStop(attempt) || timeStrategy.shouldStop(attempt))
                .withWaitStrategy(WaitStrategies.exponentialWait(100, 5, TimeUnit.SECONDS))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        System.out.println(String.format("第%d次重试, 已经过时间：%d ms",
                                attempt.getAttemptNumber(),
                                attempt.getDelaySinceFirstAttempt()));
                        if (attempt.hasException()) {
                            System.out.println("发生异常：" + attempt.getExceptionCause().getMessage());
                        }
                    }
                })
                .build();

        try {
            return retryer.call(() -> rpcClient.sendRequest(request));
        } catch (ExecutionException e) {
            System.out.println("重试最终失败：" + e.getMessage());
            throw new InterruptedException(e.getMessage());
        } catch (RetryException e) {
            System.out.println("达到重试限制：" + e.getMessage());
            throw new InterruptedException(e.getMessage());
        }
    }
}