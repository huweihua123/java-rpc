package server.serviceCenter;

import java.net.InetSocketAddress;

public interface ServiceRegister {
    void register(String serviceName, InetSocketAddress inetSocketAddress);
}
