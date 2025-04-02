package com.weihua.client.proxy;

import com.weihua.client.circuitBreaker.CircuitBreaker;
import com.weihua.client.circuitBreaker.CircuitBreakerProvider;
import com.weihua.client.retry.GuavaRetry;
import com.weihua.client.rpcClient.RpcClient;
import com.weihua.client.rpcClient.impl.NettyRpcClient;
import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.client.serverCenter.impl.ConsulServiceCenter;
import com.weihua.trace.interceptor.ClientTraceInterceptor;
import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.UUID;

@Log4j2
public class ClientProxy implements InvocationHandler {
    private RpcClient rpcClient;
    private ServiceCenter serviceCenter;
    private CircuitBreakerProvider circuitBreakerProvider;

    public ClientProxy() {
        this.rpcClient = new NettyRpcClient();
        // this.serviceCenter = new ZkServiceCenter();
        this.serviceCenter = ConsulServiceCenter.getInstance();
        this.circuitBreakerProvider = new CircuitBreakerProvider();
    }

    public ClientProxy(ServiceCenter serviceCenter) {
        this.serviceCenter = ConsulServiceCenter.getInstance();
        this.rpcClient = new NettyRpcClient(serviceCenter); // 传入已有实例
        this.circuitBreakerProvider = new CircuitBreakerProvider();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ClientTraceInterceptor.beforeInvoke();
        RpcRequest rpcRequest = RpcRequest.builder().requestId(UUID.randomUUID().toString())
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName()).params(args).paramTypes(method.getParameterTypes()).build();
        CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(method.getDeclaringClass().getName());
        if (!circuitBreaker.allowRequest()) {
            log.warn("熔断器开启，请求被拒绝: {}", rpcRequest);
            return null;
        }
        RpcResponse rpcResponse;

        String methodSignature = getMethodSignature(rpcRequest.getInterfaceName(), method);
        log.info("方法签名: " + methodSignature);
        InetSocketAddress inetSocketAddress = serviceCenter.serviceDiscovery(rpcRequest);

        if (serviceCenter.checkRetry(inetSocketAddress, methodSignature)) {
            try {
                log.info("尝试重试调用服务: {}", methodSignature);
                GuavaRetry guavaRetry = new GuavaRetry();
                rpcResponse = guavaRetry.sendServiceWithRetry(rpcRequest, rpcClient);
            } catch (Exception e) {
                log.error("重试调用失败: {}", methodSignature, e);
                circuitBreaker.recordFailure();
                throw e; // 将异常抛给调用者
            }
        } else {
            rpcResponse = rpcClient.sendRequest(rpcRequest);
        }

        if (rpcResponse != null) {
            if (rpcResponse.getCode() == 200) {
                circuitBreaker.recordSuccess();
            } else {
                circuitBreaker.recordFailure();
            }
        }
        log.info("收到响应: {} 状态码: {}", rpcRequest.getInterfaceName(), rpcResponse.getCode());

        ClientTraceInterceptor.afterInvoke(method.getName());
        return rpcResponse != null ? rpcResponse.getData() : null;
    }

    public <T> T getProxy(Class<T> clazz) {
        Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, this);
        return (T) o;
    }

    public void close() {
        rpcClient.close();
    }

    private String getMethodSignature(String interfaceName, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(interfaceName).append("#").append(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            sb.append(parameterTypes[i].getName());
            if (i < parameterTypes.length - 1) {
                sb.append(",");
            } else {
                sb.append(")");
            }
        }
        return sb.toString();
    }
}
