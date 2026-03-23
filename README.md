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
| **监控追踪** | TraceCollector 链路追踪 + Metrics 指标收集 |
| **可视化** | Admin Dashboard 实时监控面板 |
| **Spring Boot** | Spring Boot 2.7.x 集成，支持热配置 |

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
│  │  IdleStateHandler → HeartbeatHandler → Encoder │     │
│  └──────────────────────────────────────────────────┘     │
└──────────────────────────┼──────────────────────────────────┘
                           │ TCP
┌──────────────────────────▼──────────────────────────────────┐
│                          Server                              │
│  ┌──────────────────────────────────────────────────┐     │
│  │         ServerChannelInitializer (Pipeline)        │     │
│  │  IdleStateHandler → HeartbeatHandler → Decoder    │     │
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
│  │  DemoServiceImpl / UserService / OrderService ...  │     │
│  └───────────────────────────────────────────────────┘     │
│                                                               │
│  ┌───────────────────────────────────────────────────┐     │
│  │              ZooRegistry                           │     │
│  │  registerService() → Zookeeper                    │     │
│  │  queryForInstances() → Service Discovery          │     │
│  └───────────────────────────────────────────────────┘     │
│                                                               │
│  ┌───────────────────────────────────────────────────┐     │
│  │           TraceCollector (Metrics)                  │     │
│  │  - QPS / 成功率 / 延迟百分位 (P50/P90/P99)        │     │
│  │  - 链路追踪 (TraceId)                              │     │
│  └───────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────┘
```

---

## 快速开始

### 环境要求
- Java 11+ (兼容 Java 8)
- Maven 3.6+
- Docker (用于 ZooKeeper)

### 1. 一键启动演示

```bash
# 克隆仓库
git clone https://github.com/zyy/snack.git
cd snack

# 运行演示（自动构建并启动所有服务）
chmod +x demo/run-demo.sh
./demo/run-demo.sh
```

演示将自动：
1. 🚀 启动 ZooKeeper 容器（Docker）
2. 🔨 构建整个项目
3. 📡 启动 Service Provider（端口 9999）
4. 🌐 启动 Web Consumer（端口 8080）
5. 📊 启动 Admin Dashboard（端口 8081）
6. ✅ 执行测试 RPC 调用

### 2. 访问服务

| 服务 | 地址 | 说明 |
|------|------|------|
| Web Consumer | http://localhost:8080 | RPC 消费者，访问 `/` 触发调用 |
| Admin Dashboard | http://localhost:8081 | 可视化监控面板 |
| ZooKeeper | localhost:2181 | 服务注册中心 |

### 3. 测试 RPC 调用

```bash
# 触发 RPC 调用
curl http://localhost:8080/

# 查看服务列表
curl http://localhost:8081/service/list

# 查看追踪数据
curl http://localhost:8081/api/traces/recent?limit=10

# 查看指标数据
curl http://localhost:8081/api/metrics/all
```

---

## 模块说明

```
snack/
├── snack-rpc                  # 核心 RPC 框架
│   └── src/main/java/com/snack/rpc/
│       ├── RpcServer.java              # 服务端（Netty Server）
│       ├── RpcClient.java             # 客户端（获取代理）
│       ├── RpcApplication.java         # Spring Boot 入口
│       ├── codec/                     # 编解码器
│       ├── client/                    # 客户端组件
│       │   ├── RpcInvoker.java        # 调用器
│       │   ├── RpcFuture.java         # 异步 Future
│       │   ├── CircuitBreaker.java    # 熔断器
│       │   ├── RateLimiter.java       # 令牌桶限流
│       │   └── RetryPolicy.java       # 重试策略
│       ├── server/                    # 服务端组件
│       │   └── ServerHandler.java      # 请求处理
│       ├── registry/                  # 注册中心
│       ├── serialization/             # 序列化
│       ├── spi/                      # SPI 扩展
│       └── trace/                    # 追踪指标
│           ├── TraceCollector.java    # 指标收集器
│           └── CircuitBreakerRegistry.java  # 熔断器注册表
│
├── snack-contract-demo         # 接口定义示例
├── snack-service-demo          # 服务端示例
├── snack-web-demo              # Web 消费者示例
└── snack-admin                 # 管理后台
    └── src/main/java/com/snack/admin/
        ├── controller/                # 控制器
        │   ├── ServiceController.java  # 服务列表
        │   ├── MetricsController.java  # 指标 API
        │   └── TraceController.java    # 追踪 API
        └── service/                    # 服务层
```

---

## 配置说明

### application.conf 示例

```hocon
# 服务配置
server {
  port = 9999
  name = "com.snack.demo.service"
}

# ZooKeeper 注册中心
zookeeper {
  basePath = "/snack/serviceDiscovery"
  connectString = "localhost:2181"
}

# 追踪配置
tracing {
  enabled = true
  sampleRate = 100    # 采样率 0-100%
}

# RPC 配置
rpc {
  invoke.timeout = 5000
  
  # 重试配置
  retry {
    enable = true
    maxAttempts = 3
    strategy = "EXPONENTIAL"
  }
  
  # 熔断配置
  circuitBreaker {
    enable = true
    failureThreshold = 5
    recoveryTimeoutMs = 30000
  }
  
  # 限流配置
  rateLimit {
    qps = 100
    burst = 50
  }
}
```

### 重试策略

| 策略 | 描述 |
|------|------|
| `FIXED` | 固定延迟 100ms |
| `EXPONENTIAL` | 指数退避 100ms → 200ms → 400ms... |
| `FIBONACCI` | 斐波那契退避 |
| `JITTER` | 纯随机延迟 |

### 熔断器状态机

```
CLOSED（正常）
  │ 连续失败 >= 5 次
  ▼
OPEN（熔断）
  │ 等待 30s
  ▼
HALF_OPEN（试探）
  │ 成功 → CLOSED
  │ 失败 → OPEN
```

---

## SPI 扩展机制

框架支持通过 SPI 机制扩展：

| SPI 接口 | 默认实现 | 说明 |
|----------|----------|------|
| `SerializerSPI` | ProtoStuffSerializer | 序列化 |
| `LoadBalanceSPI` | RoundRobin, Random | 负载均衡 |
| `RegistrySPI` | ZooKeeperRegistry | 注册中心 |

添加自定义实现：
1. 实现对应接口
2. 在 `META-INF/services/` 下注册
3. 配置中指定使用

---

## Admin Dashboard 功能

### Dashboard 首页
- 📊 服务调用量实时统计（QPS）
- 📈 接口响应时间图表（avg, P50, P90, P99）
- ✅ 成功率/失败率展示
- 🔴 熔断器状态面板

### 服务详情
- 📋 服务实例列表
- 📉 调用指标详细数据
- ⚡ 熔断器状态和配置
- 🔧 在线参数调整

### API 端点

```
GET  /api/metrics/all              # 所有服务聚合指标
GET  /api/metrics/service/{name}   # 特定服务详细指标
GET  /api/traces/recent?limit=100   # 最近调用链路
GET  /api/health                   # 系统健康检查
```

---

## 依赖版本

| 组件 | 版本 |
|------|------|
| Java | 11+ (兼容 8) |
| Spring Boot | 2.7.18 |
| Spring Framework | 5.3.x |
| Netty | 4.1.108.Final |
| Curator | 2.7.1 |
| ProtoStuff | 1.3.5 |
| Lombok | 1.18.30 |
| Zookeeper | 3.4.6 |

---

## 开发

### 本地构建

```bash
# 编译所有模块
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package -DskipTests
```

### 手动启动各服务

```bash
# 1. 启动 ZooKeeper
docker run -d --name zookeeper -p 2181:2181 zookeeper:3.8

# 2. 构建项目
mvn clean install -DskipTests

# 3. 启动服务提供者
cd snack-service-demo
mvn spring-boot:run

# 4. 启动 Web 消费者（新终端）
cd snack-web-demo
mvn spring-boot:run

# 5. 启动 Admin（新终端）
cd snack-admin
mvn spring-boot:run
```

---

## License

Apache License 2.0
