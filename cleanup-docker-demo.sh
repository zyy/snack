#!/bin/bash

# Snack RPC Demo 清理脚本
# 停止并删除所有演示容器

echo "🧹 清理 Snack RPC Demo 容器..."

containers=("snack-zookeeper" "snack-service-provider" "snack-web-consumer")

for container in "${containers[@]}"; do
    if docker ps -a --format '{{.Names}}' | grep -q "^${container}$"; then
        echo "停止容器: $container"
        docker rm -f "$container" > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            echo "  ✅ $container 已删除"
        else
            echo "  ❌ 删除 $container 失败"
        fi
    else
        echo "  ℹ️  $container 不存在，跳过"
    fi
done

echo ""
echo "✅ 清理完成!"
echo ""
echo "要完全清理 Maven 依赖缓存，可以删除 ~/.m2/repository/com/snack"
echo "但通常不需要这样做，除非有版本冲突问题"