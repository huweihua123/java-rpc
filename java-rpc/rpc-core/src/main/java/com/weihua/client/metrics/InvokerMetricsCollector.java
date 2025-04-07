package com.weihua.client.metrics;

import com.weihua.client.invoker.Invoker;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Invoker性能指标收集器
 * 收集并汇总各个Invoker的性能指标，为负载均衡决策提供依据
 */
@Log4j2
public class InvokerMetricsCollector {
    private static final InvokerMetricsCollector INSTANCE = new InvokerMetricsCollector();

    // 按地址存储的性能指标
    private final Map<String, ServiceMetrics> metricsMap = new ConcurrentHashMap<>();

    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "invoker-metrics-collector");
        t.setDaemon(true);
        return t;
    });

    private boolean started = false;

    private InvokerMetricsCollector() {
    }

    public static InvokerMetricsCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 启动收集器
     */
    public void start() {
        if (!started) {
            synchronized (this) {
                if (!started) {
                    // 启动定时任务，定期收集并打印性能指标
                    scheduler.scheduleAtFixedRate(
                            this::collectAndPrintMetrics,
                            30, 60, TimeUnit.SECONDS);
                    started = true;
                    log.info("Invoker性能指标收集器已启动");
                }
            }
        }
    }

    /**
     * 收集和打印性能指标
     */
    private void collectAndPrintMetrics() {
        try {
            log.info("========== Invoker性能指标汇总 ==========");

            for (Map.Entry<String, ServiceMetrics> entry : metricsMap.entrySet()) {
                String serviceName = entry.getKey();
                ServiceMetrics metrics = entry.getValue();

                log.info("服务: {}", serviceName);
                log.info("  总请求数: {}", metrics.getTotalRequests());
                log.info("  平均响应时间: {}ms", String.format("%.2f", metrics.getAvgResponseTime()));
                log.info("  成功率: {}%", String.format("%.2f", metrics.getSuccessRate() * 100));
                log.info("  活跃连接数: {}", metrics.getActiveConnections());
                log.info("  活跃请求数: {}", metrics.getActiveRequests());
                log.info("-------------------------------------");
            }

            log.info("======================================");
        } catch (Exception e) {
            log.error("收集性能指标时发生异常", e);
        }
    }

    /**
     * 记录请求开始
     */
    public void recordRequestStart(String serviceName, Invoker invoker) {
        getOrCreateMetrics(serviceName).recordRequestStart(invoker);
    }

    /**
     * 记录请求结束
     */
    public void recordRequestEnd(String serviceName, Invoker invoker, long responseTimeMs, boolean success) {
        getOrCreateMetrics(serviceName).recordRequestEnd(invoker, responseTimeMs, success);
    }

    /**
     * 更新Invoker列表
     */
    public void updateInvokers(String serviceName, Map<InetSocketAddress, Invoker> invokers) {
        getOrCreateMetrics(serviceName).updateInvokers(invokers);
    }

    /**
     * 获取或创建服务指标
     */
    private ServiceMetrics getOrCreateMetrics(String serviceName) {
        return metricsMap.computeIfAbsent(serviceName, k -> new ServiceMetrics(serviceName));
    }

    /**
     * 关闭收集器
     */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                log.info("Invoker性能指标收集器已关闭");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
    }

    /**
     * 服务级别的性能指标
     */
    private static class ServiceMetrics {
        private final String serviceName;
        private final Map<InetSocketAddress, Invoker> invokers = new ConcurrentHashMap<>();

        private long totalRequests = 0;
        private long successfulRequests = 0;
        private long totalResponseTime = 0;

        public ServiceMetrics(String serviceName) {
            this.serviceName = serviceName;
        }

        /**
         * 记录请求开始
         */
        public void recordRequestStart(Invoker invoker) {
            // 当前实现简单记录请求数
            // 实际实现可以根据需要扩展
        }

        /**
         * 记录请求结束
         */
        public void recordRequestEnd(Invoker invoker, long responseTimeMs, boolean success) {
            totalRequests++;
            totalResponseTime += responseTimeMs;

            if (success) {
                successfulRequests++;
            }
        }

        /**
         * 更新Invoker列表
         */
        public void updateInvokers(Map<InetSocketAddress, Invoker> newInvokers) {
            invokers.clear();
            invokers.putAll(newInvokers);
        }

        /**
         * 获取总请求数
         */
        public long getTotalRequests() {
            return totalRequests;
        }

        /**
         * 获取平均响应时间
         */
        public double getAvgResponseTime() {
            if (totalRequests == 0) {
                return 0;
            }
            return (double) totalResponseTime / totalRequests;
        }

        /**
         * 获取成功率
         */
        public double getSuccessRate() {
            if (totalRequests == 0) {
                return 1.0;
            }
            return (double) successfulRequests / totalRequests;
        }

        /**
         * 获取活跃连接数
         */
        public int getActiveConnections() {
            return invokers.size();
        }

        /**
         * 获取活跃请求数
         */
        public int getActiveRequests() {
            int total = 0;
            for (Invoker invoker : invokers.values()) {
                total += invoker.getActiveCount();
            }
            return total;
        }
    }
}
