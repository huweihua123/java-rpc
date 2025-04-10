/*
 * @Author: weihua hu
 * @Date: 2025-04-10 15:04:16
 * @LastEditTime: 2025-04-10 15:04:18
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * RPC服务提供者启动类
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = { "com.weihua.rpc" })
public class RpcProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RpcProviderApplication.class, args);
        log.info("RPC服务提供者启动成功!");
    }
}
