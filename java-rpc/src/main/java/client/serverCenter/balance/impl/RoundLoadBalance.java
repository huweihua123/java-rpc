package client.serverCenter.balance.impl;

import client.serverCenter.balance.LoadBalance;

import java.util.List;

public class RoundLoadBalance implements LoadBalance {
    private int choose;

    @Override
    public String balance(List<String> addressList) {
        choose++;
        choose = choose % addressList.size();
        System.out.println("负载均衡选择了" + choose + "服务器");
        return addressList.get(choose);
    }
}
