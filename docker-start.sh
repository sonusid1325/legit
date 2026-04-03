#!/bin/bash

echo "🐳 LEGIT KYC PLATFORM - DOCKER DEPLOYMENT"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# Check if docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if docker-compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

echo "✅ Docker detected"
echo "✅ Docker Compose detected"
echo ""

# Stop any running instances
echo "🛑 Stopping any existing containers..."
docker-compose down 2>/dev/null || true
echo ""

# Build and start
echo "🏗️  Building containers..."
echo "   This may take a few minutes on first run..."
docker-compose build --no-cache

echo ""
echo "🚀 Starting services..."
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be healthy..."

# Wait for services
for i in {1..60}; do
    HEALTH=$(docker-compose ps --services --filter "status=running" | wc -l)
    if [ "$HEALTH" -eq "3" ]; then
        echo "✅ All services are running!"
        break
    fi
    echo -n "."
    sleep 2
done

echo ""
echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "✅ DEPLOYMENT COMPLETE!"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""
echo "📋 Service Status:"
docker-compose ps
echo ""
echo "🔗 Access Points:"
echo "   Backend API:    http://localhost:8080"
echo "   Health Check:   http://localhost:8080/api/v1/gateway/health"
echo "   Blockchain RPC: http://localhost:8545"
echo "   MongoDB:        localhost:${MONGODB_HOST_PORT:-27018}"
echo ""
echo "📊 View Logs:"
echo "   docker-compose logs -f"
echo ""
echo "🧪 Test Deployment:"
echo "   curl http://localhost:8080/api/v1/gateway/health | jq"
echo ""
echo "📖 Full Documentation:"
echo "   See DOCKER_DEPLOYMENT.md for complete guide"
echo ""
echo "═══════════════════════════════════════════════════════════════════════════"

# Wait a bit more and test
sleep 5

echo ""
echo "🧪 Testing deployment..."
HEALTH_CHECK=$(curl -s http://localhost:8080/api/v1/gateway/health | jq -r '.data.status' 2>/dev/null || echo "WAITING")

if [ "$HEALTH_CHECK" == "HEALTHY" ]; then
    echo "✅ Health check PASSED!"
    echo ""
    curl -s http://localhost:8080/api/v1/gateway/health | jq '.data.services'
else
    echo "⏳ Backend is still starting up..."
    echo "   Run: docker-compose logs -f backend"
    echo "   Wait for: 'BlockchainService — Blockchain Audit Trail (LIVE)'"
fi

echo ""
echo "🎉 Your Legit KYC platform is running with blockchain integration!"
echo ""
