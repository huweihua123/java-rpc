package com.weihua.client.serverCenter.handler;

import com.weihua.client.pool.InvokerManager;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 服务地址变更处理器
 * 负责处理各种注册中心的地址变更事件，并将其转发到InvokerManager
 */
@Log4j2
public class ServiceAddressChangeHandler {
    private static final ServiceAddressChangeHandler INSTANCE = new ServiceAddressChangeHandler();

    // 维护服务名到地址列表的映射关系
    private final Map<String, List<String>> serviceAddressMap = new ConcurrentHashMap<>();

    // Invoker管理器
    private final InvokerManager invokerManager;

    private ServiceAddressChangeHandler() {
        this.invokerManager = InvokerManager.getInstance();
    }

    public static ServiceAddressChangeHandler getInstance() {
        return INSTANCE;
    }

    /**
     * 为指定服务创建地址变更监听器
     * 
     * @param serviceName 服务名称
     * @return 地址变更监听函数
     */
    public Consumer<List<String>> createAddressChangeListener(String serviceName) {
        return addressList -> handleAddressChange(serviceName, addressList);
    }

    /**
     * 处理地址变更事件
     * 
     * @param serviceName    服务名称
     * @param newAddressList 新的地址列表
     */
    public void handleAddressChange(String serviceName, List<String> newAddressList) {
        if (newAddressList == null) {
            return;
        }

        log.info("接收到服务[{}]地址变更事件，新地址数量: {}", serviceName, newAddressList.size());

        // 保存新的地址列表
        List<String> oldAddressList = serviceAddressMap.put(serviceName, newAddressList);

        // 处理新增的地址
        invokerManager.addAddresses(newAddressList);

        // 处理下线的地址
        invokerManager.removeAddresses(newAddressList);

        log.info("服务[{}]地址变更处理完成", serviceName);
    }

    /**
     * 手动添加或更新服务地址列表
     * 
     * @param serviceName 服务名称
     * @param addressList 地址列表
     */
    public void updateServiceAddresses(String serviceName, List<String> addressList) {
        if (addressList == null) {
            return;
        }

        handleAddressChange(serviceName, addressList);
    }

    /**
     * 获取服务当前的地址列表
     * 
     * @param serviceName 服务名称
     * @return 地址列表，不存在时返回null
     */
    public List<String> getServiceAddresses(String serviceName) {
        return serviceAddressMap.get(serviceName);
    }

    /**
     * 清理指定服务的地址缓存
     * 
     * @param serviceName 服务名称
     */
    public void clearServiceAddresses(String serviceName) {
        List<String> removed = serviceAddressMap.remove(serviceName);
        if (removed != null) {
            log.info("清理服务[{}]的地址缓存，移除{}个地址", serviceName, removed.size());
        }
    }

    /**
     * 清理所有服务的地址缓存
     */
    public void clearAllServiceAddresses() {
        serviceAddressMap.clear();
        log.info("已清理所有服务的地址缓存");
    }
}
