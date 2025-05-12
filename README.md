# Java RPC Framework

A simple but powerful RPC framework.

## 模块

本项目包含以下模块：

*   **rpc-common**: 包含 RPC 框架中各模块共享的通用类和工具。
*   **rpc-core**: RPC 框架的核心实现，包括序列化、网络通信、服务注册与发现等。
*   **rpc-spring**: 提供与 Spring Framework 集成的支持。
*   **rpc-spring-boot-starter**: 为 Spring Boot 应用程序提供自动配置和便捷的启动器。
*   **rpc-example**: 包含使用此 RPC 框架的示例代码，演示了服务提供者和消费者的实现。

## 主要特性 (预期)

*   高性能的远程过程调用
*   支持多种序列化协议 (例如 Protostuff, FastJSON)
*   基于 Netty 的网络通信
*   服务注册与发现 (例如 Consul, ZooKeeper)
*   负载均衡策略
*   Spring 和 Spring Boot 集成
*   可扩展的设计，易于添加新的功能

## 先决条件

*   JDK 17 或更高版本
*   Maven 3.x

## 构建

要构建项目，请在项目根目录运行以下命令：

```bash
mvn clean install
```

## 运行示例

`rpc-example` 模块包含了如何使用此框架的示例。您可以参考该模块下的子模块来运行服务提供者和消费者。

通常，您需要：
1. 启动服务注册中心（如 Consul 或 ZooKeeper）。
2. 运行 `rpc-example-provider` 模块中的服务提供者。
3. 运行 `rpc-example-consumer` 模块中的服务消费者来调用远程服务。

具体步骤请参考 `rpc-example` 模块下的 README 或代码。

## 贡献

欢迎为此项目做出贡献！请随时提交 Pull Request 或创建 Issue。

## 未来工作 (Future Work)

本项目也作为一个学习项目，欢迎大家参与和贡献。后续计划包括：

1.  **代码相关知识点细节剖析文档**: 撰写详细文档，深入剖析项目中涉及的核心技术点和实现细节。
    *   知识点解析文档链接：[Spring RPC 知识点解析](https://bcnbeu66iny0.feishu.cn/wiki/SbqiwBdm2i6XftkvM0KcjVBMnUg)
2.  **面经准备**: 整理和总结与本项目技术栈相关的常见面试问题和解答，帮助学习者更好地准备技术面试。

## 许可证

[待定 - 请选择一个许可证，例如 Apache 2.0 或 MIT]
