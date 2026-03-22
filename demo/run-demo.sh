#!/bin/bash

# Snack RPC Framework Demo Script (Improved Version)
# This script runs a complete demo of the Snack RPC framework

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."
cd "$PROJECT_DIR"

# Create logs directory
LOGS_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOGS_DIR"

# Backup original configs
BACKUP_DIR="$SCRIPT_DIR/config-backup"
mkdir -p "$BACKUP_DIR"

cleanup() {
    echo ""
    echo "========================================"
    echo "Cleaning up..."
    echo "========================================"
    
    # Kill background processes
    if [ -n "$SERVICE_PID" ]; then
        echo "Stopping Service Provider (PID: $SERVICE_PID)..."
        kill $SERVICE_PID 2>/dev/null || true
    fi
    
    if [ -n "$WEB_PID" ]; then
        echo "Stopping Web Consumer (PID: $WEB_PID)..."
        kill $WEB_PID 2>/dev/null || true
    fi
    
    # Stop ZooKeeper container
    echo "Stopping ZooKeeper container..."
    docker-compose -f "$SCRIPT_DIR/docker-compose.yml" down 2>/dev/null || true
    
    # Restore original configs if they were backed up
    if [ -f "$BACKUP_DIR/application-service.conf" ]; then
        echo "Restoring original service configuration..."
        cp "$BACKUP_DIR/application-service.conf" snack-service-demo/src/main/resources/application.conf
    fi
    if [ -f "$BACKUP_DIR/application-web.conf" ]; then
        echo "Restoring original web configuration..."
        cp "$BACKUP_DIR/application-web.conf" snack-web-demo/src/main/resources/application.conf
    fi
    
    echo "Cleanup completed."
    echo "Logs are available in: $LOGS_DIR"
}

# Set up trap for cleanup
trap cleanup EXIT INT TERM

echo "========================================"
echo "Snack RPC Framework Demo"
echo "========================================"

# Check prerequisites
echo "Checking prerequisites..."
command -v java >/dev/null 2>&1 || { echo "❌ Java is required but not installed. Aborting."; exit 1; }
echo "✅ Java: $(java -version 2>&1 | head -1)"

command -v mvn >/dev/null 2>&1 || { echo "❌ Maven is required but not installed. Aborting."; exit 1; }
echo "✅ Maven: $(mvn --version 2>&1 | head -1)"

command -v docker >/dev/null 2>&1 || { echo "❌ Docker is required but not installed. Aborting."; exit 1; }
echo "✅ Docker: $(docker --version 2>&1 | head -1)"

# Start ZooKeeper
echo ""
echo "Starting ZooKeeper..."
if ! docker-compose -f "$SCRIPT_DIR/docker-compose.yml" ps | grep -q "Up"; then
    docker-compose -f "$SCRIPT_DIR/docker-compose.yml" up -d
    echo "Waiting for ZooKeeper to be ready..."
    
    # Wait for ZooKeeper to be ready
    for i in {1..10}; do
        if echo ruok | nc localhost 2181 2>/dev/null | grep -q imok; then
            echo "✅ ZooKeeper is ready after $i attempts"
            break
        elif [ $i -eq 10 ]; then
            echo "❌ ZooKeeper failed to start within 50 seconds"
            docker-compose -f "$SCRIPT_DIR/docker-compose.yml" logs
            exit 1
        else
            echo "Attempt $i: Waiting for ZooKeeper..."
            sleep 5
        fi
    done
else
    echo "✅ ZooKeeper is already running"
fi

# Build the project
echo ""
echo "Building Snack RPC project..."
mvn clean compile -DskipTests

# Backup original configs and create demo configuration
echo ""
echo "Configuring demo..."
cp snack-service-demo/src/main/resources/application.conf "$BACKUP_DIR/application-service.conf" 2>/dev/null || true
cp snack-web-demo/src/main/resources/application.conf "$BACKUP_DIR/application-web.conf" 2>/dev/null || true

cp "$SCRIPT_DIR/application-service.conf" snack-service-demo/src/main/resources/application.conf
cp "$SCRIPT_DIR/application-web.conf" snack-web-demo/src/main/resources/application.conf

echo "========================================"
echo "Starting Snack RPC Demo"
echo "========================================"

# Start Service Provider
echo ""
echo "1. Starting Service Provider (port 9999)..."
cd snack-service-demo
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=9999" > "$LOGS_DIR/service.log" 2>&1 &
SERVICE_PID=$!
cd ..

echo "Waiting for service to start (max 30 seconds)..."
for i in {1..30}; do
    if curl -s --connect-timeout 2 http://localhost:9999 2>/dev/null > /dev/null; then
        echo "✅ Service Provider is up after $i seconds"
        break
    elif [ $i -eq 30 ]; then
        echo "❌ Service Provider failed to start within 30 seconds"
        echo "Service logs (last 50 lines):"
        tail -50 "$LOGS_DIR/service.log"
        exit 1
    else
        sleep 1
    fi
done

# Start Web Consumer
echo ""
echo "2. Starting Web Consumer (port 8080)..."
cd snack-web-demo
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080" > "$LOGS_DIR/web.log" 2>&1 &
WEB_PID=$!
cd ..

echo "Waiting for web consumer to start (max 30 seconds)..."
for i in {1..30}; do
    if curl -s --connect-timeout 2 http://localhost:8080 2>/dev/null > /dev/null; then
        echo "✅ Web Consumer is up after $i seconds"
        break
    elif [ $i -eq 30 ]; then
        echo "❌ Web Consumer failed to start within 30 seconds"
        echo "Web logs (last 50 lines):"
        tail -50 "$LOGS_DIR/web.log"
        exit 1
    else
        sleep 1
    fi
done

echo ""
echo "========================================"
echo "Demo is running!"
echo "========================================"
echo "Service Provider: http://localhost:9999"
echo "Web Consumer:     http://localhost:8080"
echo "Logs directory:   $LOGS_DIR"
echo ""

# Test the RPC call
echo "Testing RPC call..."
for i in {1..10}; do
    RESPONSE=$(curl -s --connect-timeout 5 http://localhost:8080/demo/hello 2>/dev/null || true)
    if [[ -n "$RESPONSE" && "$RESPONSE" == *"hello"* ]]; then
        echo "✅ Success! Response from RPC call: $RESPONSE"
        break
    else
        echo "Attempt $i: Response was '$RESPONSE', waiting..."
        
        # Show logs on 5th attempt for debugging
        if [ $i -eq 5 ]; then
            echo "=== Service Logs (last 20 lines) ==="
            tail -20 "$LOGS_DIR/service.log"
            echo "=== Web Logs (last 20 lines) ==="
            tail -20 "$LOGS_DIR/web.log"
        fi
        
        sleep 3
    fi
done

if [[ -z "$RESPONSE" || "$RESPONSE" != *"hello"* ]]; then
    echo "❌ Failed to get valid response from RPC call"
    echo "=== Full Service Log ==="
    cat "$LOGS_DIR/service.log"
    echo "=== Full Web Log ==="
    cat "$LOGS_DIR/web.log"
    exit 1
fi

echo ""
echo "========================================"
echo "Demo Components"
echo "========================================"
echo "- ZooKeeper:      localhost:2181 (service registry)"
echo "- Service:        localhost:9999 (RPC service provider)"
echo "- Web Consumer:   localhost:8080 (RPC consumer)"
echo "- Service Log:    $LOGS_DIR/service.log"
echo "- Web Log:        $LOGS_DIR/web.log"
echo ""
echo "Additional commands:"
echo "  View service logs:      tail -f $LOGS_DIR/service.log"
echo "  View web logs:          tail -f $LOGS_DIR/web.log"
echo "  View ZooKeeper logs:    docker-compose -f $SCRIPT_DIR/docker-compose.yml logs -f"
echo "  Test RPC call again:    curl http://localhost:8080/demo/hello"
echo ""
echo "Press Ctrl+C to stop the demo..."
echo ""

# Wait for Ctrl+C
wait