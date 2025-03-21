package client.serverCenter;

import java.net.InetSocketAddress;

public interface ServiceCenter {
    InetSocketAddress serviceDiscovery(String serviceName);

    boolean checkRetry(String serviceName);
}