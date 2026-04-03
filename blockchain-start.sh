#!/bin/bash
set -e

echo "🚀 Starting Ganache Blockchain..."
echo "════════════════════════════════════════════════════════════"

ganache \
  --chain.chainId 1337 \
  --wallet.deterministic \
  --server.host 0.0.0.0 \
  --server.port 8545 \
  --chain.vmErrorsOnRPCResponse true \
  --wallet.totalAccounts 10 \
  --wallet.defaultBalance 1000 \
  > /var/log/ganache.log 2>&1 &

GANACHE_PID=$!
echo "✅ Ganache started (PID: $GANACHE_PID)"

echo "⏳ Waiting for blockchain to be ready..."
for i in {1..30}; do
  if curl -s -X POST http://localhost:8545 \
      -H "Content-Type: application/json" \
      -d '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' > /dev/null 2>&1; then
    echo "✅ Blockchain is ready!"
    break
  fi
  sleep 1
done

echo ""
echo "📋 Blockchain Configuration:"
echo "════════════════════════════════════════════════════════════"
curl -s -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_accounts","params":[],"id":1}' \
  | jq -r '.result[] | "Account: \(.)"' | head -3

echo ""
echo "🚀 Deploying Smart Contracts..."
echo "════════════════════════════════════════════════════════════"

cd /blockchain
node deploy-local.js

if [ -f deployment.json ]; then
  cp deployment.json /data/deployment.json
  echo "✅ Deployment info saved to /data/deployment.json"
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "✅ Blockchain Ready!"
echo "════════════════════════════════════════════════════════════"
echo "RPC Endpoint:     http://0.0.0.0:8545"
echo "Chain ID:         1337"
echo "Deployment Info:  /data/deployment.json"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "📊 Streaming blockchain logs..."
tail -f /var/log/ganache.log
