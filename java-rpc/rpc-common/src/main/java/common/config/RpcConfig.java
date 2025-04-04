/*
 * @Author: weihua hu
 * @Date: 2025-04-05 00:09:29
 * @LastEditTime: 2025-04-05 00:09:31
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.config;

import lombok.extern.log4j.Log4j2;
import java.util.Arrays;
import java.util.List;

/**
 * RPC配置门面类
 * 提供简化的配置访问方式
 */
@Log4j2
public class RpcConfig {
    private static final ConfigurationManager CONFIG = ConfigurationManager.getInstance();

    // 应用配置
    public static String appName() {
        return CONFIG.getString("rpc.application.name", "default-rpc-app");
    }

    public static String appOwner() {
        return CONFIG.getString("rpc.application.owner", "unknown");
    }

    public static String appVersion() {
        return CONFIG.getString("rpc.application.version", "1.0.0");
    }

    // 注册中心配置
    public static class Registry {
        public static String type() {
            return CONFIG.getString("rpc.registry.type", "local");
        }

        public static boolean registerEnabled() {
            return CONFIG.getBoolean("rpc.provider.registry.enable", true);
        }

        public static boolean subscribeEnabled() {
            return CONFIG.getBoolean("rpc.registry.subscribe", true);
        }

        public static class Consul {
            public static String host() {
                return CONFIG.getString("rpc.registry.consul.host", "localhost");
            }

            public static int port() {
                return CONFIG.getInt("rpc.registry.consul.port", 8500);
            }

            public static int timeout() {
                return CONFIG.getInt("rpc.registry.consul.timeout", 10000);
            }

            public static String checkInterval() {
                return CONFIG.getString("rpc.registry.consul.check.interval", "10s");
            }
        }

        public static class ZooKeeper {
            public static String address() {
                return CONFIG.getString("rpc.registry.zookeeper.address", "127.0.0.1:2181");
            }

            public static int sessionTimeout() {
                return CONFIG.getInt("rpc.registry.zookeeper.session.timeout", 40000);
            }
        }
    }

    // 提供者配置
    public static class Provider {
        public static String host() {
            return CONFIG.getString("rpc.provider.host", "auto");
        }

        public static int port() {
            return CONFIG.getInt("rpc.provider.port", 9999);
        }

        public static int ioThreads() {
            return CONFIG.getInt("rpc.provider.threads.io", Runtime.getRuntime().availableProcessors());
        }

        public static int workerThreads() {
            return CONFIG.getInt("rpc.provider.threads.worker", 200);
        }

        public static int maxConnections() {
            return CONFIG.getInt("rpc.provider.connections.max", 10000);
        }

        public static boolean dynamicRegister() {
            return CONFIG.getBoolean("rpc.provider.registry.dynamic", true);
        }

        public static String[] getServiceInterfaces() {
            int size = CONFIG.getInt("rpc.services.size", 0);
            String[] interfaces = new String[size];

            for (int i = 0; i < size; i++) {
                interfaces[i] = CONFIG.getString("rpc.services[" + i + "].interface", "");
            }

            return interfaces;
        }

        public static String getServiceImplementation(String interfaceName) {
            int size = CONFIG.getInt("rpc.services.size", 0);

            for (int i = 0; i < size; i++) {
                String iface = CONFIG.getString("rpc.services[" + i + "].interface", "");
                if (interfaceName.equals(iface)) {
                    return CONFIG.getString("rpc.services[" + i + "].implementation", "");
                }
            }

            return "";
        }
    }

    // 消费者配置
    public static class Consumer {
        public static int timeout() {
            return CONFIG.getInt("rpc.consumer.timeout", 3000);
        }

        public static String loadBalance() {
            return CONFIG.getString("rpc.consumer.loadbalance", "random");
        }

        public static String failoverPolicy() {
            return CONFIG.getString("rpc.consumer.failover.policy", "failover");
        }

        public static boolean retryEnabled() {
            return CONFIG.getBoolean("rpc.consumer.retry.enable", true);
        }

        public static int retryMaxTimes() {
            return CONFIG.getInt("rpc.consumer.retry.max.times", 3);
        }

        public static int retryInterval() {
            return CONFIG.getInt("rpc.consumer.retry.interval", 1000);
        }

        public static boolean connectionPoolEnabled() {
            return CONFIG.getBoolean("rpc.consumer.connection.pool.enable", true);
        }

        public static List<String> getReferences() {
            String refs = CONFIG.getString("rpc.references", "");
            if (refs.isEmpty()) {
                return List.of();
            }
            return Arrays.asList(refs.split(","));
        }
    }

    // 心跳配置
    public static class Heartbeat {
        public static int writerIdleTime() {
            return CONFIG.getInt("rpc.heartbeat.writer.idle.time", 8);
        }

        public static int readerIdleTime() {
            return CONFIG.getInt("rpc.heartbeat.reader.idle.time", 0);
        }

        public static int allIdleTime() {
            return CONFIG.getInt("rpc.heartbeat.all.idle.time", 0);
        }

        public static String timeUnit() {
            return CONFIG.getString("rpc.heartbeat.time.unit", "SECONDS");
        }
    }

    // 序列化配置
    public static String serializer() {
        return CONFIG.getString("rpc.serializer", "json");
    }
}