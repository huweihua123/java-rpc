<!--
 * @Author: weihua hu
 * @Date: 2025-04-02 19:14:36
 * @LastEditTime: 2025-04-02 19:14:55
 * @LastEditors: weihua hu
 * @Description: 
-->
# Consul Watch机制说明

## 简介

Consul提供了Watch机制，用于监听各种资源（如KV存储、服务、事件等）的变化。本项目使用了Consul的服务健康状态Watch机制，实现对服务实例变化的实时监听。

## 项目中的Watch实现

我们的实现基于consul-client库的`ServiceHealthCache`，它提供了基于HTTP长轮询(Long Polling)的Watch机制：

1. **长轮询(Long Polling)**: 不同于普通轮询，长轮询在服务端没有变化时会保持连接打开状态，直到有变化或超时。

2. **阻塞查询**: 通过设置`waitTime`参数（默认为10秒），请求会在服务端等待，直到有数据变化或超时。

3. **监听机制**: 当服务健康状态发生变化时，`ServiceHealthCache`会触发监听器回调。

## 代码示例

```java
// 创建监听缓存
ServiceHealthCache serviceHealthCache = ServiceHealthCache.newCache(
    consulClient.healthClient(),
    serviceName,       // 监听的服务名
    false,             // 是否只包含健康服务
    10,                // 等待时间（秒）
    QueryOptions.BLANK // 查询选项
);

// 添加监听器
serviceHealthCache.addListener(newValues -> {
    // 处理服务变化
});

// 启动监听
serviceHealthCache.start();
```

## Watch机制优势

1. **实时性**: 服务状态变化能够近实时地被检测到
2. **效率**: 长轮询比短周期轮询更高效
3. **一致性**: 保证客户端视图与Consul服务器的一致性

## 补充保障机制

虽然Watch机制很可靠，但我们仍然实现了周期性刷新作为额外的保障措施：

```java
executorService.scheduleAtFixedRate(this::refreshServices, 30, 60, TimeUnit.SECONDS);
```

这确保了即使Watch机制因网络问题失效，系统仍能在较短时间内自我恢复。
