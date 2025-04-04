<!--
 * @Author: weihua hu
 * @Date: 2025-04-04 21:14:41
 * @LastEditTime: 2025-04-04 21:14:42
 * @LastEditors: weihua hu
 * @Description: 
-->
# RPC服务提供者指南

## 配置文件说明

RPC提供者模块包含以下配置文件：

1. **rpc-provider.properties** - 主配置文件，包含所有RPC提供者配置
2. **services.yaml** - 服务定义配置，用于声明要暴露的服务
3. **log4j2.xml** - 日志配置文件
4. **reference.properties** - 参考配置文件，包含所有可用配置项及说明

## 快速开始

### 1. 定义服务接口

在`rpc-api`模块中定义服务接口：

```java
package com.weihua.service;

public interface HelloService {
    String sayHello(String name);
}
```

### 2. 实现服务接口

在`rpc-provider`模块中实现服务接口：

```java
package com.weihua.service.impl;

import com.weihua.service.HelloService;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name;
    }
}
```

### 3. 配置服务

在`services.yaml`中添加服务配置：

```yaml
services:
  - interface: com.weihua.service.HelloService
    implementation: com.weihua.service.impl.HelloServiceImpl
    version: 1.0.0
    group: default
```

### 4. 启动服务提供者

运行`ProviderApplication`类启动服务提供者。

## 配置优先级

配置加载优先级从高到低：

1. JVM系统属性 (`-Dproperty=value`)
2. 环境变量
3. `rpc-provider.properties`
4. `rpc-core.properties`
5. 默认值

## 常见问题

1. **Q: 如何修改服务端口?**  
   A: 在`rpc-provider.properties`中设置`rpc.provider.port=端口号`

2. **Q: 如何使用不同的注册中心?**  
   A: 在`rpc-provider.properties`或`rpc-core.properties`中设置`rpc.registry=zookeeper|consul|local`

3. **Q: 如何设置服务限流?**  
   A: 在`rpc-provider.properties`中设置`rpc.provider.rate.limit=QPS值`
