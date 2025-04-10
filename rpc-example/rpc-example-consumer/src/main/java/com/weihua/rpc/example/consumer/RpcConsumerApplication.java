/*
 * @Author: weihua hu
 * @Date: 2025-04-10 15:05:43
 * @LastEditTime: 2025-04-10 15:05:45
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * RPC服务消费者启动类
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = { "com.weihua.rpc" })
public class RpcConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RpcConsumerApplication.class, args);
        log.info("RPC服务消费者启动成功!");
    }
}
