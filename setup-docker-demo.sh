#!/bin/bash

# Snack RPC Demo - Docker 环境设置脚本
# 这个脚本会设置完整的 Snack RPC 演示环境
# 包括 ZooKeeper、Service Provider (端口 9999) 和 Web Consumer (端口 8080)

set -e  # 遇到错误退出

echo "========================================"
echo "🎉 Snack RPC Docker Demo 环境设置"
echo "========================================"

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker 未运行或当前用户无权访问"
    echo "请确保 Docker Desktop 已启动，并重新运行此脚本"
    exit 1
fi

echo "✅ Docker 已就绪"

# 创建演示配置
echo "📝 创建演示配置文件..."
cat > /tmp/service-config.conf << 'EOF'
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
EOF

cat > /tmp/web-config.conf << 'EOF'
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
EOF

echo "✅ 配置文件已创建"

# 清理旧的容器（可选）
echo "🧹 清理旧的容器..."
docker rm -f snack-zookeeper 2>/dev/null || true

# 启动 ZooKeeper
echo "🐘 启动 ZooKeeper 容器..."
docker run -d \
  --name snack-zookeeper \
  -p 2181:2181 \
  --restart unless-stopped \
  zookeeper:3.8

echo "⏳ 等待 ZooKeeper 启动..."
for i in {1..30}; do
    if docker exec snack-zookeeper bash -c "echo ruok | nc localhost 2181" | grep -q "imok"; then
        echo "✅ ZooKeeper 已就绪 ($i 秒)"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ ZooKeeper 启动超时"
        exit 1
    fi
    echo "等待 ZooKeeper... ($i/30)"
    sleep 1
done

# 创建 Maven Docker 运行函数
run_maven_in_docker() {
    local workdir="$1"
    local cmd="$2"
    
    docker run -it --rm \
      -v "${workdir}:/app" \
      -v "${HOME}/.m2:/root/.m2" \
      --network host \
      -w /app \
      maven:3.9.9-eclipse-temurin-8 \
      bash -c "${cmd}"
}

# 构建项目
echo "🔨 构建项目..."
cd "$(dirname "$0")"
run_maven_in_docker "$(pwd)" "mvn clean compile -DskipTests"

echo "✅ 项目构建完成"

# 复制配置文件
echo "📋 复制配置文件到模块..."
cp /tmp/service-config.conf snack-service-demo/src/main/resources/application.conf
cp /tmp/web-config.conf snack-web-demo/src/main/resources/application.conf

echo "✅ 配置文件已复制"

# 启动服务提供者
echo "🚀 启动 Service Provider (端口 9999)..."
echo "注意: Service Provider 将在后台运行"
echo "要查看日志，请打开新的终端并运行: docker logs -f snack-service-provider"

# 停止旧的服务容器（如果存在）
docker rm -f snack-service-provider 2>/dev/null || true

docker run -d \
  --name snack-service-provider \
  -v "$(pwd):/app" \
  -v "${HOME}/.m2:/root/.m2" \
  --network host \
  -w /app/snack-service-demo \
  maven:3.9.9-eclipse-temurin-8 \
  mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=9999"

echo "⏳ 等待 Service Provider 启动..."
for i in {1..30}; do
    if curl -s --connect-timeout 2 http://localhost:9999/actuator/health 2>/dev/null | grep -q "UP"; then
        echo "✅ Service Provider 已就绪 ($i 秒)"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Service Provider 启动超时"
        echo "查看日志: docker logs snack-service-provider"
        exit 1
    fi
    echo "等待 Service Provider... ($i/30)"
    sleep 1
done

# 启动 Web Consumer
echo "🌐 启动 Web Consumer (端口 8080)..."
echo "注意: Web Consumer 将在后台运行"
echo "要查看日志，请打开新的终端并运行: docker logs -f snack-web-consumer"

# 停止旧的Web容器（如果存在）
docker rm -f snack-web-consumer 2>/dev/null || true

docker run -d \
  --name snack-web-consumer \
  -v "$(pwd):/app" \
  -v "${HOME}/.m2:/root/.m2" \
  --network host \
  -w /app/snack-web-demo \
  maven:3.9.9-eclipse-temurin-8 \
  mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080"

echo "⏳ 等待 Web Consumer 启动..."
for i in {1..30}; do
    if curl -s --connect-timeout 2 http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        echo "✅ Web Consumer 已就绪 ($i 秒)"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Web Consumer 启动超时"
        echo "查看日志: docker logs snack-web-consumer"
        exit 1
    fi
    echo "等待 Web Consumer... ($i/30)"
    sleep 1
done

# 测试 RPC 调用
echo "🔍 测试 RPC 调用..."
sleep 5  # 给服务一些时间注册

for i in {1..10}; do
    RESPONSE=$(curl -s --connect-timeout 5 http://localhost:8080/demo/hello || true)
    if [[ -n "$RESPONSE" && "$RESPONSE" == *"hello"* ]]; then
        echo "✅ RPC 调用成功! 响应: $RESPONSE"
        DEMO_STATUS="success"
        break
    else
        echo "尝试 $i: 响应为 '$RESPONSE', 等待..."
        
        if [ $i -eq 5 ]; then
            echo "=== Service Provider 日志 (最后20行) ==="
            docker logs snack-service-provider --tail 20
            echo "=== Web Consumer 日志 (最后20行) ==="
            docker logs snack-web-consumer --tail 20
        fi
        
        sleep 3
    fi
done

if [ -z "$DEMO_STATUS" ]; then
    echo "❌ RPC 调用失败"
    echo "=== Service Provider 完整日志 ==="
    docker logs snack-service-provider
    echo "=== Web Consumer 完整日志 ==="
    docker logs snack-web-consumer
    exit 1
fi

echo ""
echo "========================================"
echo "🎉 Snack RPC Demo 设置完成!"
echo "========================================"
echo ""
echo "📊 组件状态:"
echo "  - ZooKeeper: localhost:2181 ✓"
echo "  - Service Provider: localhost:9999 ✓"
echo "  - Web Consumer: localhost:8080 ✓"
echo "  - RPC 调用: '$RESPONSE' ✓"
echo ""
echo "🔧 管理命令:"
echo "  查看服务日志: docker logs -f snack-service-provider"
echo "  查看Web日志:  docker logs -f snack-web-consumer"
echo "  查看ZooKeeper日志: docker logs -f snack-zookeeper"
echo "  停止所有服务: docker rm -f snack-zookeeper snack-service-provider snack-web-consumer"
echo ""
echo "🌐 测试端点:"
echo "  健康检查: curl http://localhost:9999/actuator/health"
echo "  RPC调用:  curl http://localhost:8080/demo/hello"
echo ""
echo "💡 提示: 所有组件都在 Docker 容器中运行，清理简单!"
echo "========================================"