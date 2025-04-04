/*
 * @Author: weihua hu
 * @Date: 2025-04-04 15:54:59
 * @LastEditTime: 2025-04-04 15:55:34
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.serverCenter.impl;

import com.weihua.client.serverCenter.ServiceCenter;
import com.weihua.server.serviceCenter.impl.LocalServiceRegisterImpl;
import common.message.RpcRequest;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;

/**
 * 本地服务发现实现，用于开发测试环境
 * 不依赖外部注册中心，在单机内存中查询服务
 */
@Log4j2
public class LocalServiceCenter implements ServiceCenter {

    private final LocalServiceRegisterImpl serviceRegister;

    public LocalServiceCenter() {
        // 尝试从Spring上下文或其他地方获取已存在的LocalServiceRegisterImpl实例
        // 这里简化处理，直接创建一个新实例
        this.serviceRegister = new LocalServiceRegisterImpl();
        log.info("初始化本地服务发现中心");
    }

    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest rpcRequest) {
        String serviceName = rpcRequest.getInterfaceName();
        InetSocketAddress address = serviceRegister.lookup(serviceName);

        if (address != null) {
            log.info("发现服务: {}, 地址: {}:{}",
                    serviceName, address.getHostName(), address.getPort());
            return address;
        }

        log.warn("未找到服务: {}", serviceName);
        return null;
    }

    @Override
    public boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature) {
        // 本地实现简化处理，不支持重试检查
        return false;
    }

    @Override
    public void close() {
        // 本地实现无需关闭资源
    }

    @Override
    public String toString() {
        return "local";
    }
}
/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-04 15:54:59
 * 
 * @LastEditTime: 2025-04-04 15:54:59
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
