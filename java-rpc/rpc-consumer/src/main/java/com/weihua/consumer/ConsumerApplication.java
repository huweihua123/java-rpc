/*
 * @Author: weihua hu
 * @Date: 2025-04-04 22:51:08
 * @LastEditTime: 2025-04-05 02:10:41
 * @LastEditors: weihua hu
 * @Description: 
 */

package com.weihua.consumer;

import lombok.extern.log4j.Log4j2;

/**
 * RPC消费者应用程序入口
 */
@Log4j2
public class ConsumerApplication {
    public static void main(String[] args) {
        log.info("正在启动RPC消费者...");
        ConsumerBootstrap.main(args);
    }
}
