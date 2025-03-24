package com.weihua.server.provider;

import com.weihua.server.rateLimit.provider.RateLimitProvider;
import com.weihua.server.serviceCenter.ServiceRegister;
import com.weihua.server.serviceCenter.impl.ZkServiceRegisterImpl;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class ServiceProvider {
    private Map<String, Object> interfaceProvider;

    private RateLimitProvider rateLimitProvider;

    private int port;

    private String host;

    private ServiceRegister serviceRegister;

    public ServiceProvider(String host, int port) {
        this.host = host;
        this.port = port;
        this.interfaceProvider = new HashMap<>();
        this.serviceRegister = new ZkServiceRegisterImpl();
        this.rateLimitProvider = new RateLimitProvider();
    }

    public void provideServiceInterface(Object service) {
        Class<?>[] interfaces = service.getClass().getInterfaces();
        for (Class<?> clazz : interfaces) {

            interfaceProvider.put(clazz.getName(), service);
            serviceRegister.register(clazz.getName(), new InetSocketAddress(host, port));
        }
    }

    public RateLimitProvider getRateLimitProvider() {
        return rateLimitProvider;
    }

    public Object getService(String interfaceName) {
        return interfaceProvider.get(interfaceName);
    }

}
