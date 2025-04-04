/*
 * @Author: weihua hu
 * @Date: 2025-04-04 21:04:33
 * @LastEditTime: 2025-04-04 22:49:30
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.provider;

import lombok.extern.log4j.Log4j2;

/**
 * RPC服务提供者应用程序入口
 */
@Log4j2
public class ProviderApplication {
    public static void main(String[] args) {
        log.info("正在启动RPC服务提供者...");
        ProviderBootstrap.main(args);
    }
}