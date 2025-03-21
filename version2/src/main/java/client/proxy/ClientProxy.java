package client.proxy;

import client.retry.GuavaRetry;
import client.rpcClient.RpcClient;
import client.rpcClient.impl.NettyRpcClient;
import client.serverCenter.ServiceCenter;
import client.serverCenter.impl.ZkServiceCenter;
import common.message.RpcRequest;
import common.message.RpcResponse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ClientProxy implements InvocationHandler {
    private RpcClient rpcClient;
    private ServiceCenter serviceCenter;

    public ClientProxy() {
        this.rpcClient = new NettyRpcClient();
        this.serviceCenter = new ZkServiceCenter();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RpcRequest rpcRequest = RpcRequest.builder().interfaceName(method.getDeclaringClass().getName()).methodName(method.getName()).params(args).paramTypes(method.getParameterTypes()).build();
        RpcResponse rpcResponse;

        if (!serviceCenter.checkRetry(method.getDeclaringClass().getName())) {
            GuavaRetry guavaRetry = new GuavaRetry();
            rpcResponse = guavaRetry.sendServiceWithRetry(rpcRequest, rpcClient);
        } else {
            rpcResponse = rpcClient.sendRequest(rpcRequest);
        }

        return rpcResponse.getData();
    }

    public <T> T getProxy(Class<T> clazz) {
        Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, this);
        return (T) o;
    }

}
