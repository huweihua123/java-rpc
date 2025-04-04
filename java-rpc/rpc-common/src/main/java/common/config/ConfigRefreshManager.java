/*
 * @Author: weihua hu
 * @Date: 2025-04-04 21:52:45
 * @LastEditTime: 2025-04-04 21:52:47
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.config;

import lombok.extern.log4j.Log4j2;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 配置刷新管理器
 * 用于统一管理各个组件的配置刷新
 */
@Log4j2
public class ConfigRefreshManager {
    private static volatile ConfigRefreshManager instance;
    private final List<ConfigurableComponent> components = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ConfigRefreshManager() {
        ConfigurationManager config = ConfigurationManager.getInstance();
        int refreshPeriod = config.getInt("rpc.config.refresh.period", 60);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "config-refresh-thread");
            t.setDaemon(true);
            return t;
        });

        if (config.getBoolean("rpc.config.auto.refresh", true)) {
            start(refreshPeriod);
        }
    }

    public static ConfigRefreshManager getInstance() {
        if (instance == null) {
            synchronized (ConfigRefreshManager.class) {
                if (instance == null) {
                    instance = new ConfigRefreshManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册可配置组件
     */
    public void register(ConfigurableComponent component) {
        components.add(component);
        log.info("组件已注册到配置刷新管理器: {}", component.getClass().getSimpleName());
    }

    /**
     * 刷新所有组件配置
     */
    public void refreshAll() {
        log.info("开始刷新所有组件配置...");
        // 首先刷新基础配置
        ConfigurationManager.getInstance().reload();

        // 然后刷新各个组件配置
        for (ConfigurableComponent component : components) {
            try {
                component.refreshConfig();
            } catch (Exception e) {
                log.error("刷新组件 {} 配置失败: {}", component.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        log.info("所有组件配置刷新完成");
    }

    /**
     * 开始定时刷新
     */
    public void start(int periodSeconds) {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(this::refreshAll,
                    periodSeconds, periodSeconds, TimeUnit.SECONDS);
            log.info("配置自动刷新已启动，周期: {}秒", periodSeconds);
        }
    }

    /**
     * 停止定时刷新
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            log.info("配置自动刷新已停止");
        }
    }

    /**
     * 可配置组件接口
     */
    public interface ConfigurableComponent {
        /**
         * 刷新配置
         */
        void refreshConfig();
    }
}