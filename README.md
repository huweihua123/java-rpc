# Java-RPC 框架

一个基于Netty和ZooKeeper的轻量级RPC框架，实现了服务注册发现、负载均衡、限流、熔断和重试等高可用特性。

## 项目结构

```
java-rpc/
├── rpc-api         # 服务接口定义和实体类
├── rpc-common      # 通用工具和序列化模块
├── rpc-core        # 框架核心功能实现
├── rpc-consumer    # 服务消费者示例
└── rpc-provider    # 服务提供者示例
```

## 核心特性

- **高性能网络通信**：基于Netty实现的高性能网络通信层
- **服务注册与发现**：基于ZooKeeper实现服务的注册与动态发现
- **多种负载均衡**：支持一致性哈希、轮询(Round Robin)和随机(Random)三种负载均衡策略
- **服务限流**：基于令牌桶算法的接口级别限流保护
- **熔断降级**：三态熔断器(关闭、开启、半开)实现服务保护
- **请求重试**：基于Guava Retry实现的失败重试机制
- **本地服务缓存**：客户端缓存服务地址，提升性能
- **服务变更监听**：实时监听服务变更，自动刷新本地缓存

## 技术架构

### 通信框架

- 基于Netty实现高性能的RPC通信
- 支持长连接复用，提高通信效率
- 自定义编解码器，解决TCP粘包拆包问题

### 服务治理

- **服务注册**：服务启动时自动注册到ZooKeeper
- **服务发现**：基于ZooKeeper的服务实时发现
- **服务监听**：监听服务变更，保持地址列表最新

### 负载均衡

框架支持多种负载均衡策略：

1. **一致性哈希(Consistency Hash)**：相同请求映射到相同服务提供者，提供会话粘性
2. **轮询(Round Robin)**：按顺序分配请求到各服务提供者
3. **随机(Random)**：随机选择服务提供者

### 高可用保障

- **熔断保护**：当服务频繁失败时自动熔断，避免雪崩效应
- **限流保障**：基于令牌桶算法实现精确的接口级限流
- **重试机制**：可配置的失败重试，提高服务调用成功率

## 快速开始

### 1. 定义服务接口

在`rpc-api`模块中定义服务接口：

```java
public interface UserService {
    // 客户端通过这个接口调用服务端的实现类
    User getUserByUserId(Integer id);
    //新增一个功能
    Integer insertUserId(User user);
}
```

### 2. 实现服务接口

在`rpc-provider`模块中实现接口：

```java
@Log4j2
public class UserServiceImpl implements UserService {
    @Override
    public User getUserByUserId(Integer id) {
        log.info("客户端查询了" + id + "的用户");
        // 模拟从数据库中取用户的行为
        Random random = new Random();
        User user = User.builder()
                .userName(UUID.randomUUID().toString())
                .id(id)
                .sex(random.nextBoolean())
                .build();
        return user;
    }

    @Override
    public Integer insertUserId(User user) {
        log.info("插入数据成功" + user.getUserName());
        return user.getId();
    }
}
```

### 3. 启动服务提供者

```java
public class TestServer {
    public static void main(String[] args) {
        UserService userService = new UserServiceImpl();
        int port = 9999;
        String host = "127.0.0.1";

        ServiceProvider serviceProvider = new ServiceProvider(host, port);
        serviceProvider.provideServiceInterface(userService);

        RpcServer rpcServer = new NettyRpcServer(serviceProvider);
        rpcServer.start(port);
    }
}
```

### 4. 服务调用（客户端）

在`rpc-consumer`模块中调用远程服务：

```java
public class ConsumerTest {
    public static void main(String[] args) {
        ClientProxy clientProxy = new ClientProxy();
        UserService proxy = clientProxy.getProxy(UserService.class);
        
        // 调用远程服务
        User user = proxy.getUserByUserId(1);
        log.info("从服务端得到的user={}", user);
        
        Integer id = proxy.insertUserId(User.builder().id(1).userName("User1").sex(true).build());
        log.info("向服务端插入user的id={}", id);
    }
}
```

## 核心组件

### 负载均衡

#### 一致性哈希实现

```java
public class ConsistencyHashBalance implements LoadBalance {
    private static final int VIRTUAL_NUM = 5;
    private final ConcurrentNavigableMap<Integer, String> shards = new ConcurrentSkipListMap<>();
    
    public String getServer(String node, List<String> serviceList) {
        // 根据请求key获取对应的服务节点
        int hash = getHash(node);
        ConcurrentNavigableMap<Integer, String> subMap = shards.tailMap(hash);
        Integer key = subMap.isEmpty() ? shards.firstKey() : subMap.firstKey();
        String virtual_node = shards.get(key);
        return virtual_node.substring(0, virtual_node.indexOf("&&"));
    }
    
    // 其他方法省略...
}
```

### 熔断器

```java
public class CircuitBreaker {
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    
    public synchronized boolean allowRequest() {
        switch (state) {
            case OPEN:
                // 熔断时间达到后尝试半开状态
                if (System.currentTimeMillis() - lastFailureTime > retryTimePeriod) {
                    state = CircuitBreakerState.HALF_OPEN;
                    resetCounts();
                }
                return false;
            case HALF_OPEN:
                // 半开状态允许有限请求通过
                return requestCount.getAndIncrement() < maxHalfOpenRequests;
            default:
                return true;
        }
    }
    
    // 其他方法省略...
}
```

### 限流器

```java
public class TokenBucketRateLimitImpl implements RateLimit {
    private AtomicInteger tokens;
    private final int capacity;
    private final int ratePerSecond;
    private long lastTokenTime;
    
    @Override
    public synchronized boolean getToken() {
        // 更新令牌桶中的令牌
        long now = System.currentTimeMillis();
        long timeElapsed = now - lastTokenTime;
        int newTokens = (int) (timeElapsed / 1000 * ratePerSecond);
        if (newTokens > 0) {
            tokens.getAndUpdate(current -> Math.min(current + newTokens, capacity));
            lastTokenTime = now;
        }
        
        // 获取令牌
        if (tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }
    
    // 其他方法省略...
}
```

## 配置选项

### ZooKeeper配置

```java
RetryPolicy policy = new ExponentialBackoffRetry(1000, 3);
this.client = CuratorFrameworkFactory.builder()
        .connectString("127.0.0.1:2182")
        .sessionTimeoutMs(40000)
        .retryPolicy(policy)
        .namespace(ROOT_PATH)
        .build();
```

### 断路器配置

```java
CircuitBreaker circuitBreaker = new CircuitBreaker(
    5,      // 5次失败触发熔断
    0.8,    // 80%成功率要求
    30000,  // 30秒熔断持续时间
    10      // 半开状态最多10次探测请求
);
```

## 未来计划

- 支持更多序列化方式（Protobuf、Hessian等）
- 扩展负载均衡策略
- 增加配置中心集成
- 提供服务治理控制台
- 支持异步调用模式
- 完善监控与追踪功能

## 参与贡献

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 开源许可

本项目采用 Apache License 2.0 开源许可证
