#!/bin/bash
set -e

echo "🚀 Starting Legit Backend with Blockchain Integration"
echo "════════════════════════════════════════════════════════════"

# Wait for blockchain to be ready and get contract addresses
echo "⏳ Waiting for blockchain service..."
until curl -s -X POST http://blockchain:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' > /dev/null 2>&1; do
  echo "   Blockchain not ready, waiting..."
  sleep 2
done
echo "✅ Blockchain is ready!"

# Check if deployment info exists
if [ -f /blockchain-data/deployment.json ]; then
  echo "📋 Reading contract addresses from blockchain..."
  
  export BLOCKCHAIN_AUDIT_CONTRACT=$(jq -r '.auditLogContract' /blockchain-data/deployment.json)
  export BLOCKCHAIN_REPUTATION_CONTRACT=$(jq -r '.reputationContract' /blockchain-data/deployment.json)
  
  echo "   AuditLog Contract:    $BLOCKCHAIN_AUDIT_CONTRACT"
  echo "   Reputation Contract:  $BLOCKCHAIN_REPUTATION_CONTRACT"
else
  echo "⚠️  No deployment info found, using simulation mode"
  export BLOCKCHAIN_AUDIT_CONTRACT=""
  export BLOCKCHAIN_REPUTATION_CONTRACT=""
fi

echo ""
echo "🔧 Configuration:"
echo "   MongoDB:     $MONGODB_URI"
echo "   Blockchain:  $BLOCKCHAIN_RPC_URL"
echo "   Chain ID:    1337"
echo "════════════════════════════════════════════════════════════"
echo ""

# Start the application
exec java $JAVA_OPTS -jar app.jar
