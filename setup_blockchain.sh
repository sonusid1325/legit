#!/bin/bash

set -e

echo "═══════════════════════════════════════════════════════════════════════════"
echo "  COMPLETE POLYGON EDGE + SMART CONTRACT DEPLOYMENT"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# Cleanup previous attempts
echo "🧹 Cleaning up previous setup..."
pkill -f polygon-edge 2>/dev/null || true
rm -rf test-chain-* genesis.json 2>/dev/null || true
sleep 2

# Step 1: Initialize validator
echo ""
echo "Step 1: Initializing validator..."
./polygon-edge secrets init --data-dir ./test-chain-1 --insecure

# Extract keys
VALIDATOR_ADDRESS=$(./polygon-edge secrets output --data-dir test-chain-1 | grep "Public key" | awk '{print $NF}')
NODE_ID=$(./polygon-edge secrets output --data-dir test-chain-1 | grep "Node ID" | awk '{print $NF}')
PRIVATE_KEY=$(cat test-chain-1/consensus/validator.key)

echo "✅ Validator initialized"
echo "   Address: $VALIDATOR_ADDRESS"
echo "   Node ID: $NODE_ID"

# Step 2: Create genesis
echo ""
echo "Step 2: Creating genesis file..."
./polygon-edge genesis \
  --consensus polybft \
  --validators-path . \
  --validators-prefix test-chain- \
  --premine 0x0000000000000000000000000000000000000000:1000000000000000000000000 \
  --premine $VALIDATOR_ADDRESS:1000000000000000000000000 \
  --native-token-config "Legit:LEGIT:18:true:$VALIDATOR_ADDRESS" \
  --reward-wallet $VALIDATOR_ADDRESS:1000000 \
  --proxy-contracts-admin $VALIDATOR_ADDRESS \
  --block-gas-limit 10000000 \
  --chain-id 1337 \
  --epoch-size 10

echo "✅ Genesis created"

# Step 3: Start Polygon Edge server
echo ""
echo "Step 3: Starting Polygon Edge server..."
nohup ./polygon-edge server \
  --data-dir ./test-chain-1 \
  --chain genesis.json \
  --grpc-address :10000 \
  --libp2p :10001 \
  --jsonrpc :8545 \
  --seal \
  --log-level INFO > polygon-edge.log 2>&1 &

EDGE_PID=$!
echo "✅ Server started (PID: $EDGE_PID)"
echo "   Waiting for server to be ready..."

# Wait for server
for i in {1..30}; do
    if curl -s -X POST http://localhost:8545 \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' > /dev/null 2>&1; then
        echo "✅ Server is ready!"
        break
    fi
    echo -n "."
    sleep 1
done

echo ""
CHAIN_ID=$(curl -s -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' | jq -r '.result')
echo "   Chain ID: $((CHAIN_ID))"

# Step 4: Deploy smart contracts
echo ""
echo "Step 4: Deploying smart contracts..."
cd contracts

# Create deployment script
cat > scripts/deploy.js << 'DEPLOY'
async function main() {
  console.log("Deploying LegitAuditLog...");
  const AuditLog = await ethers.getContractFactory("LegitAuditLog");
  const auditLog = await AuditLog.deploy();
  await auditLog.waitForDeployment();
  const auditAddress = await auditLog.getAddress();
  console.log("✅ LegitAuditLog deployed to:", auditAddress);

  console.log("\nDeploying VerifierReputation...");
  const Reputation = await ethers.getContractFactory("VerifierReputation");
  const reputation = await Reputation.deploy();
  await reputation.waitForDeployment();
  const reputationAddress = await reputation.getAddress();
  console.log("✅ VerifierReputation deployed to:", reputationAddress);
  
  // Save addresses
  const fs = require('fs');
  fs.writeFileSync('deployed-addresses.json', JSON.stringify({
    auditLog: auditAddress,
    reputation: reputationAddress
  }, null, 2));
  
  console.log("\n✅ Contract addresses saved to deployed-addresses.json");
}

main().catch(console.error);
DEPLOY

# Deploy contracts
export PRIVATE_KEY=$PRIVATE_KEY
npx hardhat run scripts/deploy.js --network edge

# Read deployed addresses
AUDIT_LOG=$(node -p "JSON.parse(require('fs').readFileSync('deployed-addresses.json')).auditLog")
REPUTATION=$(node -p "JSON.parse(require('fs').readFileSync('deployed-addresses.json')).reputation")

echo "✅ Contracts deployed"
echo "   AuditLog: $AUDIT_LOG"
echo "   Reputation: $REPUTATION"

# Step 5: Update application.yaml
echo ""
echo "Step 5: Updating application.yaml..."
cd ..

# Backup original
cp src/main/resources/application.yaml src/main/resources/application.yaml.backup

# Update blockchain section
cat > /tmp/blockchain_config.txt << BLOCKCHAIN
blockchain:
  rpcUrl: "http://localhost:8545"
  privateKey: "$PRIVATE_KEY"
  auditLogContract: "$AUDIT_LOG"
  reputationContract: "$REPUTATION"
BLOCKCHAIN

# Replace blockchain section in application.yaml
sed -i '/^blockchain:/,/^$/d' src/main/resources/application.yaml
cat /tmp/blockchain_config.txt >> src/main/resources/application.yaml

echo "✅ Configuration updated"

# Summary
echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "  ✅ SETUP COMPLETE!"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""
echo "📝 Configuration:"
echo "   RPC URL: http://localhost:8545"
echo "   Chain ID: 1337"
echo "   Validator: $VALIDATOR_ADDRESS"
echo "   AuditLog Contract: $AUDIT_LOG"
echo "   Reputation Contract: $REPUTATION"
echo ""
echo "🚀 Next steps:"
echo "   1. Start Legit backend: ./gradlew run"
echo "   2. Check health: curl http://localhost:8080/api/v1/gateway/health | jq '.data.services.blockchain'"
echo ""
echo "📊 Blockchain logs: tail -f polygon-edge.log"
echo "🛑 Stop blockchain: pkill -f polygon-edge"
echo ""

