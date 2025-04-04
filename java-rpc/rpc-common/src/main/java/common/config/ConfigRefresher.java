/*
 * @Author: weihua hu
 * @Date: 2025-04-05 19:25:00
 * @LastEditTime: 2025-04-04 21:21:59
 * @LastEditors: weihua hu
 * @Description: 配置刷新工具
 */
package common.config;

import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 配置刷新工具
 * 用于定期刷新配置或手动刷新配置
 */
@Log4j2
public class ConfigRefresher {
    private static final ConfigRefresher INSTANCE = new ConfigRefresher();
    private final List<RefreshableComponent> refreshableComponents = new ArrayList<>();
    private ScheduledExecutorService scheduler;
    private boolean scheduled = false;

    private ConfigRefresher() {
    }

    public static ConfigRefresher getInstance() {
        return INSTANCE;
    }

    /**
     * 注册可刷新组件
     * 
     * @param component         组件实例
     * @param refreshMethodName 刷新方法名
     */
    public void registerComponent(Object component, String refreshMethodName) {
        try {
            Method refreshMethod = component.getClass().getMethod(refreshMethodName);
            refreshableComponents.add(new RefreshableComponent(component, refreshMethod));
            log.debug("已注册可刷新组件: {}.{}", component.getClass().getName(), refreshMethodName);
        } catch (NoSuchMethodException e) {
            log.error("注册刷新组件失败，找不到方法: {}.{}",
                    component.getClass().getName(), refreshMethodName, e);
        }
    }

    /**
     * 刷新所有注册的组件配置
     */
    public void refreshAll() {
        log.info("开始刷新所有组件配置...");

        try {
            // 首先刷新配置管理器
            ConfigurationManager.getInstance().reload();

            // 然后刷新所有组件
            for (RefreshableComponent component : refreshableComponents) {
                try {
                    component.refresh();
                    log.info("已刷新组件: {}", component.instance.getClass().getName());
                } catch (Exception e) {
                    log.error("刷新组件失败: {}", component.instance.getClass().getName(), e);
                }
            }

            log.info("所有组件配置刷新完成");
        } catch (Exception e) {
            log.error("刷新配置过程中发生错误", e);
        }
    }

    /**
     * 启动定期刷新任务
     * 
     * @param initialDelaySeconds 初始延迟(秒)
     * @param periodSeconds       间隔时间(秒)
     */
    public synchronized void startPeriodicRefresh(int initialDelaySeconds, int periodSeconds) {
        if (scheduled) {
            log.warn("定期刷新任务已启动，忽略此次调用");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "config-refresher");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::refreshAll,
                initialDelaySeconds,
                periodSeconds,
                TimeUnit.SECONDS);

        scheduled = true;
        log.info("已启动定期配置刷新任务，间隔: {}秒", periodSeconds);
    }

    /**
     * 停止定期刷新任务
     */
    public synchronized void stopPeriodicRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduled = false;
            log.info("已停止定期配置刷新任务");
        }
    }

    /**
     * 可刷新组件封装
     */
    private static class RefreshableComponent {
        private final Object instance;
        private final Method refreshMethod;

        public RefreshableComponent(Object instance, Method refreshMethod) {
            this.instance = instance;
            this.refreshMethod = refreshMethod;
        }

        public void refresh() throws Exception {
            refreshMethod.invoke(instance);
        }
    }
}
