# Snack RPC Framework

[![Build Status](https://travis-ci.org/zyy/snack.svg?branch=master)](https://travis-ci.org/zyy/snack)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

> Snack 是一个基于 Java / Netty4 的高性能分布式 RPC 框架，支持服务注册与发现、负载均衡、异步调用、熔断、限流、重试。

---

## 特性

| 类别 | 功能 |
|------|------|
| **网络通信** | Netty4 NIO，Server/Client 双端 Pipeline |
| **序列化** | ProtoStuff 高效序列化 |
| **服务治理** | Zookeeper + Curator 服务注册/注销/发现 |
| **负载均衡** | 轮询 (RoundRobin)、随机 (Random) |
| **连接池** | Netty ChannelPool 复用连接 |
| **透明调用** | JDK Dynamic Proxy，零侵入业务代码 |
| **异步调用** | `RpcFuture` 支持 Future / Callback |
| **熔断器** | CLOSED / OPEN / HALF_OPEN 三态保护 |
| **限流** | 令牌桶 QPS 限制 + 突发容量 |
| **重试策略** | FIXED / EXPONENTIAL / FIBONACCI / JITTER |
| **心跳机制** | IdleStateHandler 自动探活 + 断线重连 |
| **Spring Boot** | `RpcApplication` / `WebApplication` 双入口 |

---

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                          Client                              │
│  ┌─────────┐    ┌──────────┐    ┌──────────────────┐     │
│  │ RpcFuture│    │CircuitBreaker│  │ RateLimiter      │     │
│  └────┬────┘    └─────┬────┘    └────────┬─────────┘     │
│       │                │                  │               │
│  ┌────▼────────────────▼──────────────────▼─────────┐     │
│  │                   RpcInvoker                      │     │
│  │  ┌──────────────────────────────────────────────┐ │     │
│  │  │ Dynamic Proxy (JDK)                         │ │     │
│  │  │  - 同步 / 异步调用                           │ │     │
│  │  │  - 失败重试 + 熔断                          │ │     │
│  │  │  - 负载均衡                                 │ │     │
│  │  └──────────────────────────────────────────────┘ │     │
│  └──────────────────────┬───────────────────────────┘     │
│                          │                                   │
│  ┌──────────────────────▼───────────────────────────┐     │
│  │            ChannelPool (Netty)                   │     │
│  │  IdleStateHandler → HeartbeatHandler → Encoder   │     │
│  └──────────────────────────────────────────────────┘     │
└──────────────────────────┼──────────────────────────────────┘
                           │ TCP
┌──────────────────────────▼──────────────────────────────────┐
│                          Server                              │
│  ┌──────────────────────────────────────────────────┐     │
│  │         ServerChannelInitializer (Pipeline)        │     │
│  │  IdleStateHandler → HeartbeatHandler → Decoder   │     │
│  └──────────────────────────┬─────────────────────────┘     │
│                             │                                   │
│  ┌─────────────────────────▼─────────────────────────┐     │
│  │              ServerHandler                         │     │
│  │  ┌──────────────────────────────────────────────┐ │     │
│  │  │ ThreadPool (Bounded)                         │ │     │
│  │  │  - 核心线程16 / 最大64 / 队列1024           │ │     │
│  │  │  - CallerRunsPolicy 防丢弃                   │ │     │
│  │  └──────────────────────────────────────────────┘ │     │
│  └──────────────────────┬──────────────────────────────┘     │
│                         │                                   │
│  ┌─────────────────────▼──────────────────────────────┐     │
│  │              Business Service                       │     │
│  │  DemoServiceImpl / UserService / OrderService ...   │     │
│  └───────────────────────────────────────────────────┘     │
│                                                               │
│  ┌───────────────────────────────────────────────────┐     │
│  │              ZooRegistry                           │     │
│  │  registerService() → Zookeeper                     │     │
│  │  queryForInstances() → Service Discovery           │     │
│  └───────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────┘
```

---

## 快速开始

### 1. 定义接口（snack-contract）

```java
public interface DemoService {
    String sayHello(String name);
    User getUser(Long userId);
}
```

### 2. 实现服务（snack-service-demo）

```java
public class DemoServiceImpl implements DemoService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
    
    @Override
    public User getUser(Long userId) {
        return new User(userId, "User-" + userId);
    }
}
```

**启动服务端：**

```java
// RpcApplication 方式
public class ServiceBootstrap {
    public static void main(String[] args) throws Exception {
        RpcServer server = new RpcServer();
        server.registerService(DemoService.class, new DemoServiceImpl());
        ZooRegistry.getInstance().registerService(
                "com.snack.demo.service",  // 服务名
                9999                        // 端口
        );
        server.start();
    }
}

// 或 Spring Boot 方式
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        RpcApplication.run(Application.class, args);
    }
}
```

**application.conf：**

```hocon
server {
  port = 9999
  name = "com.snack.demo.service"
}
zookeeper {
  connectString = "192.168.9.153:2181"
  basePath = "/snack/serviceDiscovery"
}
```

### 3. 客户端调用

```java
// 获取代理
RpcClient rpcClient = new RpcClient();
DemoService demoService = rpcClient.getProxy(DemoService.class);

// 同步调用
String result = demoService.sayHello("Snack");

// 异步调用（RpcFuture）
RpcFuture future = demoService.$async("sayHello", new Object[]{"Snack"});
Object result = future.get(3000, TimeUnit.MILLISECONDS);

// 异步回调
future.addCallback(new RpcFuture.RpcCallback() {
    @Override
    public void onSuccess(Object result) {
        System.out.println("Success: " + result);
    }
    @Override
    public void onFailure(Exception e) {
        System.out.println("Failed: " + e.getMessage());
    }
});
```

---

## 配置说明

### 服务端配置

```hocon
rpc.server {
  workerThreads = 0                    # NIO线程数（0=2*CPU核数）
  threadPool {
    corePoolSize = 16                  # 核心线程数
    maxPoolSize = 64                   # 最大线程数
    queueCapacity = 1024               # 队列容量（防OOM）
    keepAliveSeconds = 60              # 空闲线程存活时间
  }
  heartbeat {
    writerIdleTime = 10               # 写空闲发送心跳（秒）
    readerIdleTime = 30               # 读空闲超时关闭连接（秒）
  }
}
```

### 客户端配置

```hocon
rpc {
  invoke.timeout = 5000               # 调用超时（毫秒）
  
  # 重试策略
  invoke.retry {
    enable = true                      # 是否启用重试
    maxAttempts = 3                   # 最大尝试次数
    baseDelayMs = 100                 # 基础重试延迟（毫秒）
    maxDelayMs = 2000                # 最大重试延迟（毫秒）
    strategy = "EXPONENTIAL"          # 重试策略
    jitter = 0.2                      # 抖动因子（0.0~1.0）
  }
  
  # 熔断器
  circuitBreaker {
    enable = true                     # 是否启用熔断
    failureThreshold = 5              # 触发熔断的连续失败次数
    recoveryTimeoutMs = 30000        # 熔断恢复等待时间（毫秒）
    halfOpenMaxCalls = 3            # 半开状态允许的试探次数
  }
  
  # 限流器
  rateLimit {
    qps = 100                        # 每秒允许的最大请求数
    burst = 50                       # 突发容量
  }
}
```

### 重试策略

| 策略 | 描述 | 适用场景 |
|------|------|---------|
| `FIXED` | 固定延迟 | 稳定的下游服务 |
| `EXPONENTIAL` | 指数退避 (100ms → 200ms → 400ms...) | 网络不稳定、临时故障 |
| `FIBONACCI` | 斐波那契退避 | 比指数更平滑的恢复 |
| `JITTER` | 纯随机延迟 | 避免惊群效应 |

### 熔断器状态机

```
CLOSED（正常）
  │ 连续失败 >= 5 次
  ▼
OPEN（熔断）
  │ 等待 30s
  ▼
HALF_OPEN（试探）
  │ 成功 → CLOSED（恢复）
  │ 失败 → OPEN（重新熔断）
```

---

## 模块说明

```
snack/
├── snack-rpc             # 核心 RPC 框架
│   └── src/main/java/com/snack/rpc/
│       ├── RpcServer.java         # 服务端（Netty Server）
│       ├── RpcClient.java        # 客户端（获取代理）
│       ├── RpcApplication.java    # Spring Boot 入口
│       ├── codec/                # 编解码器（ProtoStuff + 心跳）
│       ├── client/               # 客户端组件
│       │   ├── RpcInvoker.java       # 调用器（含重试/熔断/限流）
│       │   ├── RpcFuture.java        # 异步 Future
│       │   ├── CircuitBreaker.java    # 熔断器
│       │   ├── RateLimiter.java      # 令牌桶限流
│       │   └── RetryPolicy.java     # 重试策略
│       ├── server/               # 服务端组件
│       │   └── ServerHandler.java     # 请求处理 + 心跳
│       └── registry/             # Zookeeper 服务注册与发现
│
├── snack-contract-demo    # 接口定义示例
├── snack-service-demo     # 服务端示例
├── snack-web-demo         # Spring MVC + RPC 调用示例
└── snack-admin           # 管理后台（查看服务列表）
```

---

## 依赖版本

| 组件 | 版本 |
|------|------|
| Java | 1.8+ |
| Netty | 4.1.108.Final |
| Curator | 2.11.1 |
| ProtoStuff | 1.0.1 |
| Lombok | 1.18.30 |
| Spring Boot | 1.3.3.RELEASE |
| Zookeeper | 3.4.6 |

---

## 开发进度

- [x] **第一阶段：稳定性**
  - [x] 超时处理（NPE 修复）
  - [x] 线程池防 OOM（有界队列 + CallerRunsPolicy）
  - [x] 参数类型匹配修复
  - [x] ZooRegistry 配置解耦
  - [x] 心跳机制

- [x] **第二阶段：可用性**
  - [x] 异步调用（RpcFuture + Callback）
  - [x] 熔断器（CircuitBreaker）
  - [x] 限流器（RateLimiter）
  - [x] 可配置重试策略

- [ ] **第三阶段：可观测性**
  - [ ] 链路追踪（TraceId 透传）
  - [ ] 监控指标（Metrics）
  - [ ] 完善 Admin 后台

- [ ] **第四阶段：扩展性**
  - [ ] 序列化 SPI 扩展
  - [ ] 负载均衡 SPI 扩展
  - [ ] 注册中心 SPI 扩展

---

## License

Apache License 2.0
