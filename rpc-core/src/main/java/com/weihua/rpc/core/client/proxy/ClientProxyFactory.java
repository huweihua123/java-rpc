package com.weihua.rpc.core.client.proxy;

import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.core.client.circuit.CircuitBreaker;
import com.weihua.rpc.core.client.circuit.CircuitBreakerProvider;
import com.weihua.rpc.core.client.config.ClientConfig;
import com.weihua.rpc.core.client.netty.NettyRpcClient;
import com.weihua.rpc.core.client.registry.ServiceDiscovery;
import com.weihua.rpc.core.condition.ConditionalOnClientMode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 客户端代理工厂
 * 为RPC接口创建动态代理
 */
@Slf4j
@Component
// @ConditionalOnProperty(name = "rpc.mode", havingValue = "client",
// matchIfMissing = false)
@ConditionalOnClientMode
public class ClientProxyFactory {

    @Autowired
    private NettyRpcClient rpcClient;

    @Autowired
    private ServiceDiscovery serviceCenter;

    @Autowired
    private CircuitBreakerProvider circuitBreakerProvider;

    @Autowired
    private ClientConfig clientConfig;

    /**
     * 创建代理对象
     *
     * @param interfaceClass 接口类
     * @param <T>            接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] { interfaceClass },
                new RpcInvocationHandler(interfaceClass));
    }

    /**
     * 创建自定义配置的代理对象
     *
     * @param interfaceClass 接口类
     * @param version        服务版本
     * @param group          服务分组
     * @param <T>            接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass, String version, String group) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] { interfaceClass },
                new RpcInvocationHandler(interfaceClass, version, group));
    }

    /**
     * RPC调用处理器
     */
    private class RpcInvocationHandler implements InvocationHandler {
        private final Class<?> interfaceClass;
        private final String version;
        private final String group;

        public RpcInvocationHandler(Class<?> interfaceClass) {
            this(interfaceClass, clientConfig.getServiceVersion(), clientConfig.getServiceGroup());
        }

        public RpcInvocationHandler(Class<?> interfaceClass, String version, String group) {
            this.interfaceClass = interfaceClass;
            this.version = version;
            this.group = group;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 跳过Object类的方法
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            String interfaceName = interfaceClass.getName();
            String methodName = method.getName();

            // 构建请求对象
            RpcRequest rpcRequest = buildRequest(method, args);

            // 获取熔断器
            CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(interfaceName);

            // 熔断器检查
            if (!circuitBreaker.allowRequest()) {
                log.warn("熔断器开启，请求被拒绝: {}", interfaceName);
                return handleCircuitBreakerOpenResult(method);
            }

            boolean success = false;
            String errorMessage = null;

            try {
                // 生成方法签名
                String methodSignature = interfaceName + "#" + methodName;
                log.debug("调用方法: {}", methodSignature);

                // 判断方法是否可重试
                boolean canRetry = clientConfig.isRetryEnable()
                        && serviceCenter.isMethodRetryable(methodSignature);

                // 发送请求
                RpcResponse response;
                if (canRetry) {
                    log.debug("调用幂等方法, 使用支持重试的调用模式");
                    response = executeWithRetry(rpcRequest);
                } else {
                    response = rpcClient.sendRequest(rpcRequest);
                }

                // 判断调用结果
                if (response != null) {
                    if (response.getCode() == 200) {
                        success = true;
                        circuitBreaker.recordSuccess();
                        return response.getData();
                    } else {
                        circuitBreaker.recordFailure();
                        errorMessage = "错误码: " + response.getCode() + ", 消息: " + response.getMessage();
                        log.warn("调用失败: {}", errorMessage);
                    }
                } else {
                    circuitBreaker.recordFailure();
                    errorMessage = "调用返回空响应";
                    log.error("调用返回空响应: {}", interfaceName);
                }

                // 抛出异常
                throw new RuntimeException("RPC调用失败: " + errorMessage);

            } catch (Exception e) {
                circuitBreaker.recordFailure();
                log.error("调用发生异常: {}", e.getMessage(), e);
                throw e;
            } finally {
                log.debug("请求 {} 结束, 成功: {}", interfaceName, success);
            }
        }

        /**
         * 构建RPC请求对象
         */
        private RpcRequest buildRequest(Method method, Object[] args) {
            return RpcRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .interfaceName(interfaceClass.getName())
                    .methodName(method.getName())
                    .parameters(args)
                    .parameterTypes(method.getParameterTypes())
                    .version(version)
                    .group(group)
                    .build();
        }

        /**
         * 使用重试机制执行请求
         */
        private RpcResponse executeWithRetry(RpcRequest request) throws Exception {
            CompletableFuture<RpcResponse> future = new CompletableFuture<>();

            // 重试处理
            int maxRetries = clientConfig.getMaxRetryAttempts();
            executeWithRetry(request, 0, maxRetries, future);

            // 等待结果
            return future.get(clientConfig.getRequestTimeout(), TimeUnit.SECONDS);
        }

        /**
         * 递归重试
         */
        private void executeWithRetry(RpcRequest request, int currentRetry, int maxRetries,
                CompletableFuture<RpcResponse> future) {
            try {
                RpcResponse response = rpcClient.sendRequest(request);

                // 请求成功，完成future
                if (response != null && response.getCode() == 200) {
                    future.complete(response);
                    return;
                }

                // 请求失败，判断是否需要重试
                if (currentRetry < maxRetries) {
                    log.warn("请求失败，准备第{}次重试", currentRetry + 1);

                    // 延迟后重试
                    long retryDelay = clientConfig.getRetryIntervalMillis();
                    CompletableFuture.delayedExecutor(retryDelay, TimeUnit.MILLISECONDS)
                            .execute(() -> executeWithRetry(request, currentRetry + 1, maxRetries, future));
                } else {
                    // 重试次数用尽，返回最后一次的响应
                    log.error("重试次数用尽，请求失败");
                    future.complete(response);
                }
            } catch (Exception e) {
                if (currentRetry < maxRetries) {
                    log.warn("请求异常，准备第{}次重试", currentRetry + 1);

                    // 延迟后重试
                    long retryDelay = clientConfig.getRetryIntervalMillis();
                    CompletableFuture.delayedExecutor(retryDelay, TimeUnit.MILLISECONDS)
                            .execute(() -> executeWithRetry(request, currentRetry + 1, maxRetries, future));
                } else {
                    // 重试次数用尽，返回异常
                    log.error("重试次数用尽，请求异常");
                    future.completeExceptionally(e);
                }
            }
        }

        /**
         * 处理熔断器打开时的返回值
         */
        private Object handleCircuitBreakerOpenResult(Method method) {
            Class<?> returnType = method.getReturnType();

            // 对于基本类型，返回默认值
            if (returnType == int.class || returnType == short.class || returnType == byte.class) {
                return 0;
            } else if (returnType == long.class) {
                return 0L;
            } else if (returnType == float.class) {
                return 0.0f;
            } else if (returnType == double.class) {
                return 0.0d;
            } else if (returnType == boolean.class) {
                return false;
            } else if (returnType == char.class) {
                return '\u0000';
            }

            // 引用类型返回null
            return null;
        }
    }
}
