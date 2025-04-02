package com.weihua.server.serviceCenter;

import java.net.InetSocketAddress;

public interface ServiceRegister {
    void register(Class<?> clazz, InetSocketAddress inetSocketAddress);
}
