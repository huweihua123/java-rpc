package com.weihua.client.serverCenter.watch;

import com.orbitz.consul.Consul;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.cache.ServiceHealthCache;
import com.orbitz.consul.model.health.ServiceHealth;
import com.weihua.client.cache.ServiceCache;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Log4j2
public class WatchConsul {
    private final Consul consulClient;
    private final ServiceCache cache;
    private final Map<String, ServiceHealthCache> watchMap;
    private final ScheduledExecutorService executorService;

    public WatchConsul(Consul consulClient, ServiceCache cache) {
        this.consulClient = consulClient;
        this.cache = cache;
        this.watchMap = new ConcurrentHashMap<>();
        this.executorService = new ScheduledThreadPoolExecutor(1);
    }

    public void startWatch() {
        // 定期获取服务列表并监听变化
        executorService.scheduleAtFixedRate(this::refreshServices, 0, 30, TimeUnit.SECONDS);
    }

    private void refreshServices() {
        try {
            // 获取所有服务名
            consulClient.catalogClient().getServices().getResponse().keySet().forEach(this::watchService);
        } catch (Exception e) {
            log.error("刷新服务列表失败", e);
        }
    }

    private void watchService(String serviceName) {
        if (watchMap.containsKey(serviceName)) {
            return;
        }

        try {
            // 创建服务健康状态的缓存监听
            ServiceHealthCache serviceHealthCache = ServiceHealthCache.newCache(consulClient.healthClient(),
                    serviceName);

            // 添加监听器
            serviceHealthCache.addListener(newValues -> {
                for (Map.Entry<String, ServiceHealth> entry : newValues.entrySet()) {
                    ServiceHealth serviceHealth = entry.getValue();
                    String address = serviceHealth.getService().getAddress() + ":"
                            + serviceHealth.getService().getPort();

                    // 根据服务健康状态决定是添加还是删除
                    if (serviceHealth.getChecks().stream().allMatch(check -> check.getStatus().equals("passing"))) {
                        cache.addServcieToCache(serviceName, address);
                        log.info("服务实例添加：{} - {}", serviceName, address);
                    } else {
                        cache.delete(serviceName, address);
                        log.info("服务实例移除：{} - {}", serviceName, address);
                    }
                }
            });

            // 启动监听
            serviceHealthCache.start();

            // 添加到监听映射
            watchMap.put(serviceName, serviceHealthCache);
            log.info("开始监听服务：{}", serviceName);

        } catch (Exception e) {
            log.error("监听服务{}失败", serviceName, e);
        }
    }

    public void stopWatch() {
        // 停止所有监听
        watchMap.values().forEach(ConsulCache::stop);
        watchMap.clear();

        // 关闭线程池
        executorService.shutdown();
        log.info("停止所有服务监听");
    }
}
/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-02 13:27:40
 * 
 * @LastEditTime: 2025-04-02 13:27:41
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
