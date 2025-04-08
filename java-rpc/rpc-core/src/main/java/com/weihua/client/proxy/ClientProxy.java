package com.weihua.client.proxy;

import com.weihua.client.circuitBreaker.CircuitBreaker;
import com.weihua.client.circuitBreaker.CircuitBreakerProvider;
import com.weihua.client.metrics.InvokerMetricsCollector;
import com.weihua.client.retry.GuavaRetry;
import com.weihua.client.rpcClient.RpcClient;
import com.weihua.client.rpcClient.impl.NettyRpcClient;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.trace.interceptor.ClientTraceInterceptor;
import common.config.ConfigurationManager;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.spi.ExtensionLoader;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.UUID;

@Log4j2
public class ClientProxy implements InvocationHandler {
    private final RpcClient rpcClient;
    private final ServiceCenter serviceCenter;
    private final CircuitBreakerProvider circuitBreakerProvider;
    private final InvokerMetricsCollector metricsCollector;

    // 配置项
    private final String serviceVersion;
    private final String serviceGroup;
    private final int timeout;
    private final boolean retryEnabled;
    private final int maxRetries;

    /**
     * 使用SPI加载默认服务中心
     */
    public ClientProxy() {
        // 使用SPI加载服务中心实现
        this.serviceCenter = ExtensionLoader.getExtensionLoader(ServiceCenter.class).getDefaultExtension();
        this.rpcClient = new NettyRpcClient(serviceCenter);
        this.circuitBreakerProvider = CircuitBreakerProvider.getInstance();
        this.metricsCollector = InvokerMetricsCollector.getInstance();

        // 加载配置
        ConfigurationManager config = ConfigurationManager.getInstance();
        this.serviceVersion = config.getString("rpc.service.version", "1.0.0");
        this.serviceGroup = config.getString("rpc.service.group", "default");
        this.timeout = config.getInt("rpc.client.request.timeout", 5);
        this.retryEnabled = config.getBoolean("rpc.client.retry.enable", true);
        this.maxRetries = config.getInt("rpc.client.retry.max", 2);

        // 启动指标收集器
        metricsCollector.start();
    }

    /**
     * 使用指定的服务中心类型
     */
    public ClientProxy(String serviceCenterType) {
        this.serviceCenter = ExtensionLoader.getExtensionLoader(ServiceCenter.class).getExtension(serviceCenterType);
        this.rpcClient = new NettyRpcClient(serviceCenter);
        this.circuitBreakerProvider = CircuitBreakerProvider.getInstance();
        this.metricsCollector = InvokerMetricsCollector.getInstance();

        // 加载配置
        ConfigurationManager config = ConfigurationManager.getInstance();
        this.serviceVersion = config.getString("rpc.service.version", "1.0.0");
        this.serviceGroup = config.getString("rpc.service.group", "default");
        this.timeout = config.getInt("rpc.client.request.timeout", 5);
        this.retryEnabled = config.getBoolean("rpc.client.retry.enable", true);
        this.maxRetries = config.getInt("rpc.client.retry.max", 2);

        // 启动指标收集器
        metricsCollector.start();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 跳过Object类的方法
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        String interfaceName = method.getDeclaringClass().getName();
        String methodName = method.getName();

        // 调用前的追踪处理
        ClientTraceInterceptor.beforeInvoke(interfaceName, methodName);

        // 构建请求对象
        RpcRequest rpcRequest = buildRequest(method, args);

        // 获取熔断器
        CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(interfaceName);

        // 熔断器检查
        if (!circuitBreaker.allowRequest()) {
            log.warn("熔断器开启，请求被拒绝: {}", rpcRequest);
            // 调用后的追踪处理
            // ClientTraceInterceptor.afterInvoke(interfaceName, methodName, false, "Circuit
            // breaker open");
            return handleCircuitBreakerOpenResult(method);
        }

        boolean success = false;
        String errorMessage = null;
        RpcResponse rpcResponse = null;
        long startTime = System.currentTimeMillis();

        try {
            // 生成方法签名并确定是否可重试
            String methodSignature = rpcRequest.getMethodSignature();
            log.info("调用方法: {}", methodSignature);

            // 从配置中心获取服务地址
            InetSocketAddress inetSocketAddress = serviceCenter.serviceDiscovery(rpcRequest);

            if (inetSocketAddress == null) {
                log.error("无法找到服务: {}", interfaceName);
                circuitBreaker.recordFailure();
                errorMessage = "服务发现失败: " + interfaceName;
                return null;
            }

            // 确认方法是否可重试
            boolean canRetry = retryEnabled && serviceCenter.checkRetry(inetSocketAddress, methodSignature);

            // 根据方法是否可重试决定调用方式
            if (canRetry) {
                log.info("调用幂等方法 {}, 使用支持重试的调用模式", methodSignature);
//                rpcResponse = executeWithRetry(rpcRequest);
                rpcResponse = rpcClient.sendRequest(rpcRequest);
            } else {
                log.info("调用非幂等方法 {}, 使用不重试的调用模式", methodSignature);
                rpcResponse = rpcClient.sendRequest(rpcRequest);
            }

            // 判断调用结果
            if (rpcResponse != null) {
                if (!rpcResponse.hasError()) {
                    success = true;
                    circuitBreaker.recordSuccess();
                } else {
                    circuitBreaker.recordFailure();
                    errorMessage = "错误码: " + rpcResponse.getCode() + ", 消息: " + rpcResponse.getMessage();
                    log.warn("调用失败: {}", errorMessage);
                }
            } else {
                circuitBreaker.recordFailure();
                errorMessage = "调用返回空响应";
                log.error("调用返回空响应: {}", interfaceName);
            }

            // 记录性能指标
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequestEnd(interfaceName, null, responseTime, success);

            // 返回结果
            log.info("收到响应: {} 状态码: {}, 耗时: {}ms",
                    rpcRequest.getInterfaceName(),
                    rpcResponse != null ? rpcResponse.getCode() : "空响应",
                    responseTime);

            return rpcResponse != null ? rpcResponse.getData() : null;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            circuitBreaker.recordFailure();
            errorMessage = e.getClass().getName() + ": " + e.getMessage();
            log.error("调用发生异常: {}", errorMessage, e);

            // 记录性能指标
            metricsCollector.recordRequestEnd(interfaceName, null, responseTime, false);
            throw e;
        } finally {
            // 调用后的追踪处理
            ClientTraceInterceptor.afterInvoke(interfaceName, methodName, success, errorMessage);
        }
    }

    /**
     * 构建RPC请求对象
     */
    private RpcRequest buildRequest(Method method, Object[] args) {
        return RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameters(args)
                .paramTypes(method.getParameterTypes())
                .serviceVersion(serviceVersion)
                .group(serviceGroup)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 使用重试机制执行请求
     */
    private RpcResponse executeWithRetry(RpcRequest request) throws Exception {
        try {
            GuavaRetry guavaRetry = new GuavaRetry();
            guavaRetry.setMaxRetries(maxRetries);
            RpcResponse response = guavaRetry.sendServiceWithRetry(request, rpcClient);
            return response;
        } catch (Exception e) {
            log.error("调用幂等方法 {} 最终失败, 所有重试均未成功", request.getMethodSignature(), e);
            throw e;
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

    /**
     * 获取代理对象
     */
    public <T> T getProxy(Class<T> clazz) {
        Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, this);
        return (T) o;
    }

    /**
     * 关闭资源
     */
    public void close() {
        if (rpcClient != null) {
            rpcClient.close();
        }
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
    }
}
