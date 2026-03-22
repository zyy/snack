#!/bin/bash

# Snack RPC Framework Demo Script
# This script runs a complete demo of the Snack RPC framework

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."
cd "$PROJECT_DIR"

# Backup original configs
BACKUP_DIR="$SCRIPT_DIR/config-backup"
mkdir -p "$BACKUP_DIR"

cleanup() {
    echo "Cleaning up..."
    # Restore original configs if they were backed up
    if [ -f "$BACKUP_DIR/application-service.conf" ]; then
        cp "$BACKUP_DIR/application-service.conf" snack-service-demo/src/main/resources/application.conf
    fi
    if [ -f "$BACKUP_DIR/application-web.conf" ]; then
        cp "$BACKUP_DIR/application-web.conf" snack-web-demo/src/main/resources/application.conf
    fi
    rm -rf "$BACKUP_DIR"
    
    # Kill background processes
    kill $SERVICE_PID $WEB_PID 2>/dev/null || true
    
    # Stop ZooKeeper
    docker-compose -f demo/docker-compose.yml down 2>/dev/null || true
    
    echo "Demo stopped."
}

trap cleanup EXIT INT TERM

echo "========================================"
echo "Snack RPC Framework Demo"
echo "========================================"

# Check prerequisites
echo "Checking prerequisites..."
command -v java >/dev/null 2>&1 || { echo "Java is required but not installed. Aborting."; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "Maven is required but not installed. Aborting."; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "Docker is required but not installed. Aborting."; exit 1; }

# Start ZooKeeper if not running
echo "Starting ZooKeeper..."
if ! docker-compose -f demo/docker-compose.yml ps | grep -q "Up"; then
    docker-compose -f demo/docker-compose.yml up -d
    echo "Waiting for ZooKeeper to be ready..."
    sleep 10
else
    echo "ZooKeeper is already running"
fi

# Build the project
echo "Building Snack RPC project..."
mvn clean compile -DskipTests

# Backup original configs and create demo configuration
echo "Backing up original configurations..."
cp snack-service-demo/src/main/resources/application.conf "$BACKUP_DIR/application-service.conf" 2>/dev/null || true
cp snack-web-demo/src/main/resources/application.conf "$BACKUP_DIR/application-web.conf" 2>/dev/null || true

echo "Creating demo configuration..."
cp demo/application-service.conf snack-service-demo/src/main/resources/application.conf
cp demo/application-web.conf snack-web-demo/src/main/resources/application.conf

echo "========================================"
echo "Starting Snack RPC Demo"
echo "========================================"

echo "1. Starting Service Provider (port 9999)..."
# Run service in background
cd snack-service-demo
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=9999" > ../demo/service.log 2>&1 &
SERVICE_PID=$!
cd ..

echo "Waiting for service to start..."
sleep 15

echo "2. Starting Web Consumer (port 8080)..."
cd snack-web-demo
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080" > ../demo/web.log 2>&1 &
WEB_PID=$!
cd ..

echo "Waiting for web to start..."
sleep 15

echo "========================================"
echo "Demo is running!"
echo "========================================"
echo "Service Provider: http://localhost:9999"
echo "Web Consumer:     http://localhost:8080"
echo ""
echo "Testing RPC call..."
echo "Making request to web consumer..."

# Test the RPC call
for i in {1..5}; do
    RESPONSE=$(curl -s http://localhost:8080/demo/hello || true)
    if [ -n "$RESPONSE" ]; then
        echo "Success! Response from RPC call: $RESPONSE"
        break
    else
        echo "Attempt $i: Waiting for services to be ready..."
        sleep 5
    fi
done

echo ""
echo "========================================"
echo "Demo Components"
echo "========================================"
echo "- ZooKeeper:      localhost:2181 (service registry)"
echo "- Service:        localhost:9999 (RPC service provider)"
echo "- Web Consumer:   localhost:8080 (RPC consumer)"
echo "- Service Log:    demo/service.log"
echo "- Web Log:        demo/web.log"
echo ""
echo "To stop the demo, press Ctrl+C"
echo ""
echo "========================================"
echo "Viewing logs..."
echo "========================================"
echo "Service logs: tail -f demo/service.log"
echo "Web logs:     tail -f demo/web.log"
echo "ZooKeeper:    docker-compose -f demo/docker-compose.yml logs -f zookeeper"

# Wait for Ctrl+C
echo ""
echo "Press Ctrl+C to stop the demo..."
wait