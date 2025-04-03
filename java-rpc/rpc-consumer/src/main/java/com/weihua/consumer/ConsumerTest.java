/*
 * @Author: weihua hu
 * @Date: 2025-03-24 16:34:15
 * @LastEditTime: 2025-04-02 17:12:08
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.consumer;

import com.weihua.client.proxy.ClientProxy;
import com.weihua.pojo.User;
import com.weihua.service.UserService;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
public class ConsumerTest {
    private static final int THREAD_POOL_SIZE = 20;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public static void main(String[] args) throws InterruptedException {

        ClientProxy clientProxy = new ClientProxy();

        UserService proxy = clientProxy.getProxy(UserService.class);

        for (int i = 0; i < 100; i++) {
            final Integer i1 = i;
            if (i % 100 == 0) {
                Thread.sleep(1);
            }

            executorService.submit(() -> {
                User user = proxy.getUserByUserId(i1);
                if (user != null) {
                    log.info("从服务端得到的user={}", user);
                } else {
                    log.warn("获取的 user 为 null, userId={}", i1);
                }
                Integer id = proxy.insertUserId(User.builder().id(i1).userName("User" + i1).sex(true).build());
                if (id != null) {
                    log.info("向服务端插入user的id={}", id);
                } else {
                    log.warn("插入失败，返回的id为null, userId={}", i1);
                }
            });
        }

        executorService.shutdown();
//        clientProxy.close();

    }
}
