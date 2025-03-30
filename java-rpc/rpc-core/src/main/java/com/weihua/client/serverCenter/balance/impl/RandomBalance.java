package com.weihua.client.serverCenter.balance.impl;


import com.weihua.client.serverCenter.balance.LoadBalance;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Random;
@Log4j2
public class RandomBalance implements LoadBalance {
    @Override
    public String balance(List<String> addressList) {
        Random random = new Random();
        int choose = random.nextInt(addressList.size());
//        System.out.println("负载均衡选择了" + choose + "服务器");
        log.info("负载均衡选择了" + choose + "服务器");
        return addressList.get(choose);
    }
}
