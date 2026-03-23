#!/bin/bash

# Snack RPC Demo 本地测试脚本
# 这个脚本会在本地启动完整的演示环境

set -e

echo "========================================"
echo "🎉 Snack RPC Demo 本地测试"
echo "========================================"

# 检查 Java
if ! java -version &> /dev/null; then
    echo "❌ Java 未安装或未在 PATH 中"
    exit 1
fi

echo "✅ Java 已安装"

# 检查 ZooKeeper
echo "🐘 检查 ZooKeeper 连接..."
if ! echo ruok | nc localhost 2181 2>/dev/null | grep -q "imok"; then
    echo "⚠️  ZooKeeper 未运行在 localhost:2181"
    echo "请先启动 ZooKeeper:"
    echo "  docker run -d --name zookeeper -p 2181:2181 zookeeper:3.8"
    echo "或手动启动 ZooKeeper 服务"
    exit 1
fi

echo "✅ ZooKeeper 已连接"

# 构建项目
echo "🔨 构建项目..."
cd "$(dirname "$0")"
mvn clean compile -DskipTests

echo "✅ 项目构建完成"

# 清理旧的进程
echo "🧹 清理旧的进程..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "java.*snack" 2>/dev/null || true
sleep 2

# 启动 Service Provider
echo "🚀 启动 Service Provider (端口 9999)..."
cd snack-service-demo
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dserver.port=9999 -Dlogging.level.com.snack=INFO" \
  > ../service.log 2>&1 &
SERVICE_PID=$!
echo "Service Provider PID: $SERVICE_PID"

echo "⏳ 等待 Service Provider 启动..."
for i in {1..45}; do
    if curl -s --connect-timeout 2 http://localhost:9999/actuator/health 2>/dev/null | grep -q "UP"; then
        echo "✅ Service Provider 已就绪 ($i 秒)"
        break
    fi
    if [ $i -eq 45 ]; then
        echo "❌ Service Provider 启动超时"
        echo "服务日志:"
        tail -50 ../service.log
        kill $SERVICE_PID 2>/dev/null || true
        exit 1
    fi
    echo "等待 Service Provider... ($i/45)"
    sleep 1
done

# 启动 Web Consumer
echo "🌐 启动 Web Consumer (端口 8080)..."
cd ../snack-web-demo
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8080 -Dlogging.level.com.snack=INFO" \
  > ../web.log 2>&1 &
WEB_PID=$!
echo "Web Consumer PID: $WEB_PID"

echo "⏳ 等待 Web Consumer 启动..."
for i in {1..45}; do
    if curl -s --connect-timeout 2 http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        echo "✅ Web Consumer 已就绪 ($i 秒)"
        break
    fi
    
    # 检查进程是否存活
    if ! kill -0 $WEB_PID 2>/dev/null; then
        echo "❌ Web Consumer 进程已终止"
        echo "Web 日志:"
        tail -50 ../web.log
        kill $SERVICE_PID 2>/dev/null || true
        exit 1
    fi
    
    if [ $i -eq 45 ]; then
        echo "❌ Web Consumer 启动超时"
        echo "Web 日志:"
        tail -50 ../web.log
        kill $SERVICE_PID $WEB_PID 2>/dev/null || true
        exit 1
    fi
    echo "等待 Web Consumer... ($i/45)"
    sleep 1
done

# 测试 RPC 调用
echo "🔍 测试 RPC 调用..."
sleep 5

DEMO_STATUS=""
for i in {1..10}; do
    RESPONSE=$(curl -s --connect-timeout 5 http://localhost:8080/demo/hello 2>/dev/null || true)
    if [[ -n "$RESPONSE" && "$RESPONSE" == *"hello"* ]]; then
        echo "✅ RPC 调用成功! 响应: $RESPONSE"
        DEMO_STATUS="success"
        break
    else
        echo "尝试 $i: 响应为 '$RESPONSE', 等待..."
        
        if [ $i -eq 5 ]; then
            echo "=== Service Provider 日志 (最后20行) ==="
            tail -20 ../service.log
            echo "=== Web Consumer 日志 (最后20行) ==="
            tail -20 ../web.log
        fi
        
        sleep 3
    fi
done

if [ -z "$DEMO_STATUS" ]; then
    echo "❌ RPC 调用失败"
    echo "=== Service Provider 完整日志 ==="
    cat ../service.log
    echo "=== Web Consumer 完整日志 ==="
    cat ../web.log
    kill $SERVICE_PID $WEB_PID 2>/dev/null || true
    exit 1
fi

echo ""
echo "========================================"
echo "🎉 Snack RPC Demo 运行成功!"
echo "========================================"
echo ""
echo "📊 组件状态:"
echo "  - ZooKeeper: localhost:2181 ✓"
echo "  - Service Provider: localhost:9999 ✓"
echo "  - Web Consumer: localhost:8080 ✓"
echo "  - RPC 调用: '$RESPONSE' ✓"
echo ""
echo "🔧 管理命令:"
echo "  查看服务日志: tail -f service.log"
echo "  查看Web日志:  tail -f web.log"
echo "  停止所有服务: kill $SERVICE_PID $WEB_PID"
echo ""
echo "🌐 测试端点:"
echo "  健康检查: curl http://localhost:9999/actuator/health"
echo "  RPC调用:  curl http://localhost:8080/demo/hello"
echo ""
echo "📝 日志文件:"
echo "  - service.log: Service Provider 日志"
echo "  - web.log: Web Consumer 日志"
echo ""
echo "要停止演示，运行: kill $SERVICE_PID $WEB_PID"
echo "========================================"

# 等待用户中断
echo ""
echo "按 Ctrl+C 停止演示..."
wait