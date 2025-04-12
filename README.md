# Java RPC Framework

## 项目概述

Java RPC Framework 是一个简单但功能强大的远程过程调用（RPC）框架，提供高性能的服务间通信解决方案，并与Spring生态系统无缝集成。

![版本](https://img.shields.io/badge/版本-1.0.0-blue)
![JDK](https://img.shields.io/badge/JDK-17-green)
![Spring](https://img.shields.io/badge/Spring-5.3.20-green)

## 核心特性

- **简单的注解驱动开发**：使用`@RpcService`和`@RpcReference`注解轻松发布和消费服务
- **灵活的服务注册与发现**：支持多种注册中心（如ZooKeeper、Consul）
- **高效的网络通信**：基于Netty实现的高性能通信框架
- **多种序列化方案**：支持Protostuff、FastJSON等序列化协议
- **服务治理能力**：超时控制、重试机制、熔断降级等
- **版本与分组管理**：支持服务的版本控制和分组隔离
- **Spring生态集成**：提供Spring Boot Starter，实现自动装配

## 项目结构

```
java-rpc/
├── rpc-common/          # 公共工具和模型定义
├── rpc-core/            # 核心实现（通信、序列化、负载均衡等）
├── rpc-spring/          # Spring集成模块
├── rpc-spring-boot-starter/ # Spring Boot自动配置
└── rpc-example/         # 使用示例
```

## 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.weihua</groupId>
    <artifactId>rpc-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 服务提供者

1. 定义服务接口

```java
public interface UserService {
    User getUserById(Long id);
    List<User> getAllUsers();
}
```

2. 实现服务接口并使用`@RpcService`注解标记

```java
@Service
@RpcService(interfaceClass = UserService.class)
public class UserServiceImpl implements UserService {
    @Override
    public User getUserById(Long id) {
        // 实现逻辑
    }
    
    @Override
    public List<User> getAllUsers() {
        // 实现逻辑
    }
}
```

### 服务消费者

使用`@RpcReference`注解注入服务代理

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @RpcReference
    private UserService userService;
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }
    
    @GetMapping("/")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }
}
```

### 配置文件

```properties
# 应用名称
spring.application.name=user-service

# RPC框架配置
rpc.registry.type=zookeeper
rpc.registry.address=localhost:2181
rpc.server.port=9090
```

## 高级特性

### 服务分组与版本控制

```java
// 提供者
@RpcService(interfaceClass = UserService.class, version = "1.0.0", group = "userGroup")
public class UserServiceImpl implements UserService { ... }

// 消费者
@RpcReference(version = "1.0.0", group = "userGroup")
private UserService userService;
```

### 超时控制

```java
@RpcReference(timeout = 5000)  // 设置5秒超时
private UserService userService;
```

### 混合服务模式

既作为服务提供者又作为服务消费者：

```java
@Service
@RpcService(interfaceClass = HybridService.class)
public class HybridServiceImpl implements HybridService {

    @RpcReference
    private UserService userService;

    @RpcReference
    private OrderService orderService;
    
    // 实现业务逻辑，同时调用其他RPC服务
}
```

## 核心类说明

- **RpcServiceBeanPostProcessor**: 处理带有`@RpcService`注解的Bean，将其注册到服务提供者
- **RpcReferenceBeanPostProcessor**: 处理带有`@RpcReference`注解的字段，注入RPC代理对象

## 性能优化

- 使用连接池管理客户端连接
- 支持多种序列化方式，可根据需求选择最优方案
- 基于Netty的高性能网络通信
- 代理对象缓存，避免重复创建

## 注意事项

- 确保服务提供者和消费者使用相同的接口定义
- 在配置文件中正确设置注册中心地址
- 服务版本号和分组要保持一致才能正确匹配

## 贡献指南

欢迎提交Issue和Pull Request，共同改进这个RPC框架。

## 许可证

本项目使用MIT许可证 - 详情请查看[LICENSE](LICENSE)文件

---

作者：weihua hu  
项目地址：[https://github.com/username/java-rpc](https://github.com/username/java-rpc)
