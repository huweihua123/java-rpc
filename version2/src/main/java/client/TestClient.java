package client;

import client.proxy.ClientProxy;
import client.rpcClient.RpcClient;
import client.rpcClient.impl.NettyRpcClient;
import common.pojo.User;
import common.service.UserService;
import common.service.impl.UserServiceImpl;

public class TestClient {
    public static void main(String[] args) {
        ClientProxy clientProxy = new ClientProxy();
        UserService proxy = clientProxy.getProxy(UserService.class);

        User user = proxy.getUserByUserId(1);
        System.out.println(user.toString());
    }
}
