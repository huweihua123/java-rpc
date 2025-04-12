/*
 * @Author: weihua hu
 * @Date: 2025-04-12 13:53:03
 * @LastEditTime: 2025-04-12 13:53:05
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.hybrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * RPC混合服务启动类
 * 同时作为服务提供者和服务消费者
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = { "com.weihua.rpc" })
public class RpcHybridApplication {

    public static void main(String[] args) {
        SpringApplication.run(RpcHybridApplication.class, args);
        log.info("RPC混合服务（提供者+消费者）启动成功!");
    }
}
