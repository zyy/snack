#!/bin/bash
set -e

echo "========================================"
echo "Snack RPC Quick Start (Docker)"
echo "========================================"

# 1. 启动 ZooKeeper
echo "🐘 启动 ZooKeeper..."
docker rm -f snack-zookeeper 2>/dev/null || true
docker run -d --name snack-zookeeper -p 2181:2181 zookeeper:3.8
echo "等待 ZooKeeper 启动..."
sleep 10
echo "✅ ZooKeeper 已就绪"

# 2. 清理旧的容器
echo "🧹 清理旧的容器..."
docker rm -f snack-service-provider snack-web-consumer 2>/dev/null || true

# 3. 复制配置文件
echo "📋 配置应用..."
cat > snack-service-demo/src/main/resources/application.conf << 'EOF'
zookeeper {
  basePath = "/snack/serviceDiscovery"
  connectString = "localhost:2181"
}
server {
  port = 9999
  name = "com.snack.demo.service"
}
tracing {
  enabled = true
  sampleRate = 100
}
spring {
  output.ansi.enabled = NEVER
}
EOF

cat > snack-web-demo/src/main/resources/application.conf << 'EOF'
zookeeper {
  basePath = "/snack/serviceDiscovery"
  connectString = "localhost:2181"
}
server {
  port = 8080
  name = "com.snack.web.demo"
}
rpc {
  invoke.timeout = 5000
  invoke.retry.enable = true
}
tracing {
  enabled = true
  sampleRate = 100
}
spring {
  output.ansi.enabled = NEVER
}
EOF

# 4. 编译项目
echo "🔨 编译项目..."
docker run --rm \
  -v "$(pwd):/app" \
  -v "$HOME/.m2:/root/.m2" \
  --network host \
  -w /app \
  maven:3.9.9-eclipse-temurin-8 \
  mvn clean compile -DskipTests

# 5. 启动 Service Provider
echo "🚀 启动 Service Provider (9999)..."
docker run -d \
  --name snack-service-provider \
  -v "$(pwd):/app" \
  -v "$HOME/.m2:/root/.m2" \
  --network host \
  -w /app/snack-service-demo \
  maven:3.9.9-eclipse-temurin-8 \
  mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspring.main.banner_mode=off -Dspring.devtools.restart.enabled=false -Dserver.port=9999"

echo "等待 Service Provider 启动..."
for i in {1..30}; do
  if curl -s --connect-timeout 2 http://localhost:9999/actuator/health 2>/dev/null | grep -q UP; then
    echo "✅ Service Provider 已就绪 ($i 秒)"
    break
  fi
  [ $i -eq 30 ] && echo "❌ Service Provider 启动超时" && exit 1
  echo "等待... ($i/30)"
  sleep 1
done

# 6. 启动 Web Consumer
echo "🌐 启动 Web Consumer (8080)..."
docker run -d \
  --name snack-web-consumer \
  -v "$(pwd):/app" \
  -v "$HOME/.m2:/root/.m2" \
  --network host \
  -w /app/snack-web-demo \
  maven:3.9.9-eclipse-temurin-8 \
  mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspring.main.banner_mode=off -Dspring.devtools.restart.enabled=false -Dserver.port=8080"

echo "等待 Web Consumer 启动..."
for i in {1..30}; do
  if curl -s --connect-timeout 2 http://localhost:8080/actuator/health 2>/dev/null | grep -q UP; then
    echo "✅ Web Consumer 已就绪 ($i 秒)"
    break
  fi
  [ $i -eq 30 ] && echo "❌ Web Consumer 启动超时" && exit 1
  echo "等待... ($i/30)"
  sleep 1
done

# 7. 测试 RPC
echo "🔍 测试 RPC 调用..."
sleep 3
RESPONSE=$(curl -s http://localhost:8080/demo/hello 2>/dev/null || echo "FAILED")
echo "响应: $RESPONSE"

echo ""
echo "========================================"
echo "🎉 Snack RPC Demo 运行成功!"
echo "========================================"
echo "  ZooKeeper:     localhost:2181"
echo "  Service:       localhost:9999"
echo "  Web Consumer:  localhost:8080"
echo "  RPC 响应:      $RESPONSE"
echo ""
echo "查看日志: docker logs -f snack-service-provider"
echo "清理:     docker rm -f snack-zookeeper snack-service-provider snack-web-consumer"