<!--
 * @Author: weihua hu
 * @Date: 2025-03-23 23:34:16
 * @LastEditTime: 2025-03-23 23:37:01
 * @LastEditors: weihua hu
 * @Description: 
-->
# Java-RPC 框架

一个基于Netty和ZooKeeper的轻量级RPC框架，实现了服务注册发现、负载均衡、限流、熔断和重试等高可用特性。

## 项目特性

- **服务注册与发现**：基于ZooKeeper实现服务的注册与动态发现
- **负载均衡**：支持轮询(Round Robin)和随机(Random)两种负载均衡策略
- **服务限流**：基于令牌桶算法的接口级别限流保护
- **熔断降级**：支持熔断器模式，防止故障扩散
- **请求重试**：基于Guava Retry实现的失败重试机制
- **高效通信**：使用Netty作为网络通信框架，支持高并发
- **服务监控**：实时监听服务变更，自动刷新本地缓存

## 架构设计

![RPC架构图](docs/images/architecture.png)

### 核心模块

- **服务提供者(Provider)**：注册并提供服务的一方
- **服务消费者(Consumer)**：发现并调用远程服务的一方
- **注册中心(Registry)**：基于ZooKeeper的服务注册与发现中心
- **负载均衡(LoadBalance)**：在多个服务提供者之间进行负载分配
- **熔断器(CircuitBreaker)**：监控调用状态，必要时进行熔断保护
- **限流器(RateLimit)**：控制服务访问频率，避免过载

## 快速开始

### 环境准备

- JDK 8+
- Maven 3.6+
- ZooKeeper 3.6+

### Maven依赖

```xml
<dependencies>
    <!-- ZooKeeper客户端 -->
    <dependency>
        <groupId>org.apache.curator</groupId>
        <artifactId>curator-recipes</artifactId>
        <version>5.1.0</version>
    </dependency>
    
    <!-- Netty网络框架 -->
    <dependency>
        <groupId>io.netty</groupId>
        <artifactId>netty-all</artifactId>
        <version>4.1.51.Final</version>
    </dependency>
    
    <!-- 重试库 -->
    <dependency>
        <groupId>com.github.rholder</groupId>
        <artifactId>guava-retrying</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
```

### 定义服务接口

```java
public interface UserService {
    User getUserByUserId(Integer id);
    Integer insertUserId(User user);
}
```

### 实现服务接口

```java
public class UserServiceImpl implements UserService {
    @Override
    public User getUserByUserId(Integer id) {
        System.out.println("服务端查询了" + id + "的用户");
        // 模拟从数据库中取用户
        User user = User.builder()
            .userName(UUID.randomUUID().toString())
            .id(id)
            .sex(new Random().nextBoolean())
            .build();
        return user;
    }

    @Override
    public Integer insertUserId(User user) {
        System.out.println("插入数据成功：" + user.getUserName());
        return user.getId();
    }
}
```

### 启动服务提供者

```java
public class TestServer {
    public static void main(String[] args) {
        UserService userService = new UserServiceImpl();
        int port = 9998;
        String host = "127.0.0.1";

        ServiceProvider serviceProvider = new ServiceProvider(host, port);
        serviceProvider.provideServiceInterface(userService);

        RpcServer rpcServer = new NettyRpcServer(serviceProvider);
        rpcServer.start(port);
    }
}
```

### 服务调用（客户端）

```java
public class TestClient {
    public static void main(String[] args) {
        ClientProxy clientProxy = new ClientProxy();
        UserService proxy = clientProxy.getProxy(UserService.class);

        // 像调用本地方法一样调用远程服务
        User user = proxy.getUserByUserId(1);
        System.out.println(user.toString());
    }
}
```

## 核心组件详解

### 1. 服务注册与发现

服务提供者启动时，自动将服务信息注册到ZooKeeper，消费者从ZooKeeper获取服务地址并进行调用。

#### 服务注册

```java
// 创建服务提供者
ServiceProvider serviceProvider = new ServiceProvider(host, port);
// 注册服务
serviceProvider.provideServiceInterface(userService);
```

#### 服务发现

```java
// 从注册中心获取服务地址
InetSocketAddress address = serviceCenter.serviceDiscovery(request.getInterfaceName());
```

### 2. 负载均衡

框架支持多种负载均衡策略，帮助客户端选择最合适的服务提供者实例。

#### 轮询策略

```java
public class RoundLoadBalance implements LoadBalance {
    private int choose;

    @Override
    public String balance(List<String> addressList) {
        choose++;
        choose = choose % addressList.size();
        return addressList.get(choose);
    }
}
```

#### 随机策略

```java
public class RandomBalance implements LoadBalance {
    @Override
    public String balance(List<String> addressList) {
        Random random = new Random();
        int choose = random.nextInt(addressList.size());
        return addressList.get(choose);
    }
}
```

### 3. 限流保护

基于令牌桶算法实现服务限流，保护服务不被过载请求压垮。

```java
// 获取接口限流器
RateLimit rateLimit = serviceProvider.getRateLimitProvider().getRateLimit(interfaceName);

// 判断是否被限流
if (!rateLimit.getToken()) {
    System.out.println(interfaceName + "被限流");
    return RpcResponse.fail();
}
```

### 4. 熔断器模式

熔断器保护服务在出现故障时，能够快速失败并防止雪崩效应。框架实现了三种状态的熔断器：

- **关闭状态(CLOSED)**：正常工作，统计失败次数
- **开启状态(OPEN)**：直接拒绝请求，一段时间后尝试半开
- **半开状态(HALF_OPEN)**：允许少量请求通过，成功率达标后恢复

```java
CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(interfaceName);
if (!circuitBreaker.allowRequest()) {
    // 熔断器开启，拒绝请求
    return null;
}

// 请求成功时记录
circuitBreaker.recordSuccess();

// 请求失败时记录
circuitBreaker.recordFailure();
```

### 5. 重试机制

使用Guava Retry实现失败重试，提高服务可用性。

```java
// 检查服务是否支持重试
if (serviceCenter.checkRetry(serviceName)) {
    GuavaRetry guavaRetry = new GuavaRetry();
    // 使用重试机制发送请求
    response = guavaRetry.sendServiceWithRetry(rpcRequest, rpcClient);
} else {
    response = rpcClient.sendRequest(rpcRequest);
}
```

## 配置说明

### ZooKeeper配置

```java
// ZooKeeper服务器地址
String zkAddress = "127.0.0.1:2182";

// 根节点路径
String rootPath = "MyRPC";

// 会话超时时间
int sessionTimeout = 40000;
```

### 限流器配置

```java
// 令牌生成间隔(ms)、令牌桶容量
RateLimit rateLimit = new TokenBucketRateLimitImpl(1, 10);
```

### 断路器配置

```java
// 失败阈值、半开状态成功率要求、重试时间周期(ms)
CircuitBreaker circuitBreaker = new CircuitBreaker(1, 0.5, 10000);
```

## 高级功能

### 服务状态监控

框架通过ZooKeeper的监听机制，实时监控服务状态变更：

- 服务上线自动发现
- 服务下线自动移除
- 服务地址变更自动更新

```java
// 设置监听
watcher.watchToUpdate(ROOT_PATH);
```

### 本地服务缓存

客户端维护服务地址的本地缓存，提高服务发现效率：

```java
// 优先从本地缓存获取服务地址
List<String> addressList = cache.getServcieFromCache(serviceName);

// 缓存未命中时从ZooKeeper获取
if (addressList == null) {
    addressList = client.getChildren().forPath("/" + serviceName);
    // 添加到缓存
    cache.addServcieToCache(serviceName, address);
}
```

## 未来计划

- 支持多种序列化方式(如Protobuf、JSON)
- 增加服务监控和指标统计功能
- 实现服务分组与版本管理
- 支持异步调用模式
- 增强服务治理能力
- 提供更完善的管理控制台

## 参与贡献

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 开源许可

本项目采用 Apache License 2.0 开源许可证
