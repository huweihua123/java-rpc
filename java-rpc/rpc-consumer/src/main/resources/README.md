<!--
 * @Author: weihua hu
 * @Date: 2025-04-04 21:14:57
 * @LastEditTime: 2025-04-04 21:14:58
 * @LastEditors: weihua hu
 * @Description: 
-->
# RPC服务消费者指南

## 配置文件说明

RPC消费者模块包含以下配置文件：

1. **rpc-consumer.properties** - 主配置文件，包含所有RPC消费者配置
2. **log4j2.xml** - 日志配置文件
3. **reference.properties** - 参考配置文件，包含所有可用配置项及说明

## 快速开始

### 1. 引入服务接口

确保已经引入包含服务接口的模块（通常是`rpc-api`）

### 2. 创建代理对象

```java
// 创建服务代理
ClientProxy clientProxy = new ClientProxy();
HelloService helloService = clientProxy.getProxy(HelloService.class);

// 调用远程方法
String result = helloService.sayHello("World");
System.out.println(result);  // 输出: Hello, World
```

### 3. 异步调用

```java
// 创建RPC请求构建器
RpcRequestBuilder builder = RpcRequestBuilder.create()
    .serviceInterface(HelloService.class)
    .methodName("sayHello")
    .arguments("Async World")
    .returnType(String.class);

// 发送异步请求
CompletableFuture<String> future = ClientProxy.asyncCall(builder);

// 处理结果
future.thenAccept(System.out::println);
```

## 配置说明

### 重要配置项

- **rpc.consumer.timeout** - 请求超时时间(毫秒)
- **rpc.consumer.retry.enable** - 是否启用重试
- **rpc.consumer.retry.max.times** - 最大重试次数
- **rpc.consumer.loadbalance** - 负载均衡策略
- **rpc.consumer.cluster.failover.policy** - 集群失败处理策略

### 连接池配置

- **rpc.consumer.connection.pool.enable** - 是否启用连接池
- **rpc.consumer.pool.max.idle** - 最大空闲连接数
- **rpc.consumer.pool.min.idle** - 最小空闲连接数

## 常见问题

1. **Q: 调用超时怎么处理?**  
   A: 可以通过设置`rpc.consumer.timeout`增加超时时间，或设置`rpc.consumer.retry.enable=true`启用重试

2. **Q: 如何处理服务不可用的情况?**  
   A: 可以配置`rpc.consumer.circuit.breaker.enable=true`启用熔断器，或使用`rpc.consumer.cluster.failover.policy=failfast`快速失败

3. **Q: 如何使用不同的负载均衡策略?**  
   A: 在配置文件中设置`rpc.consumer.loadbalance=random|roundRobin|consistentHash|leastActive`
