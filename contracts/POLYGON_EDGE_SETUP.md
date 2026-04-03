# Polygon Edge Integration Guide

You're using **Polygon Edge** - a framework for building private Ethereum-compatible blockchain networks. This is perfect for development and testing before going to mainnet.

## Current Setup Detection

Your command shows:
```bash
polygon-edge secrets init --data-dir ./data-dir1 --insecure
```

This means you've already initialized validator secrets. Let's complete the setup!

## Step 1: Initialize Multiple Validators (if not done)

For a proper test network, you need at least 4 validators:

```bash
# Initialize 4 validators
polygon-edge secrets init --data-dir ./data-dir1 --insecure
polygon-edge secrets init --data-dir ./data-dir2 --insecure
polygon-edge secrets init --data-dir ./data-dir3 --insecure
polygon-edge secrets init --data-dir ./data-dir4 --insecure
```

## Step 2: Generate Genesis File

```bash
# Create genesis with all validators
polygon-edge genesis \
  --consensus ibft \
  --ibft-validators-prefix-path data-dir \
  --bootnode /ip4/127.0.0.1/tcp/30301/p2p/$(polygon-edge secrets output --data-dir data-dir1 | grep "Node ID" | awk '{print $3}') \
  --premine 0x85da99c8a7c2c95964c8efd687e95e632fc533d6:1000000000000000000000000
```

**Or use a simpler dev genesis:**

```bash
polygon-edge genesis \
  --consensus ibft \
  --ibft-validator 0x85da99c8a7c2c95964c8efd687e95e632fc533d6 \
  --premine 0x85da99c8a7c2c95964c8efd687e95e632fc533d6:1000000000000000000000000 \
  --chain-id 1337 \
  --block-gas-limit 10000000
```

## Step 3: Start Polygon Edge Network

### Option A: Single Node (Development)

```bash
polygon-edge server \
  --data-dir ./data-dir1 \
  --chain genesis.json \
  --grpc-address :10000 \
  --libp2p :30301 \
  --jsonrpc :8545 \
  --seal
```

### Option B: Multi-Node Network (Production-like)

Terminal 1:
```bash
polygon-edge server --data-dir ./data-dir1 --chain genesis.json --grpc-address :10000 --libp2p :30301 --jsonrpc :8545 --seal
```

Terminal 2:
```bash
polygon-edge server --data-dir ./data-dir2 --chain genesis.json --grpc-address :20000 --libp2p :30302 --jsonrpc :8546 --seal
```

Terminal 3:
```bash
polygon-edge server --data-dir ./data-dir3 --chain genesis.json --grpc-address :30000 --libp2p :30303 --jsonrpc :8547 --seal
```

Terminal 4:
```bash
polygon-edge server --data-dir ./data-dir4 --chain genesis.json --grpc-address :40000 --libp2p :30304 --jsonrpc :8548 --seal
```

## Step 4: Get Your Private Key

```bash
# Extract the private key from validator secrets
cat data-dir1/consensus/validator.key
```

Or use this command:
```bash
polygon-edge secrets output --data-dir data-dir1
```

Look for the "BLS Public key" line. You need the **validator private key** from `validator.key` file.

## Step 5: Deploy Smart Contracts

### Using Remix with Polygon Edge

1. Open Remix: https://remix.ethereum.org/
2. Add custom network in MetaMask:
   - **Network Name**: `Polygon Edge Local`
   - **RPC URL**: `http://localhost:8545` (or your server IP)
   - **Chain ID**: `1337` (or whatever you set in genesis)
   - **Currency Symbol**: `MATIC`

3. Import account into MetaMask:
   - Use the private key from `data-dir1/consensus/validator.key`
   - This account should have premined tokens

4. Deploy contracts:
   - Deploy `LegitAuditLog.sol`
   - Deploy `VerifierReputation.sol`
   - **Copy both contract addresses**

### Using Hardhat (Automated)

```bash
cd contracts
npm init -y
npm install --save-dev hardhat @nomicfoundation/hardhat-toolbox
```

Create `hardhat.config.js`:
```javascript
require("@nomicfoundation/hardhat-toolbox");

module.exports = {
  solidity: "0.8.20",
  networks: {
    edge: {
      url: "http://127.0.0.1:8545",
      chainId: 1337,
      accounts: ["YOUR_VALIDATOR_PRIVATE_KEY_HERE"]
    }
  }
};
```

Deploy:
```bash
npx hardhat run scripts/deploy.js --network edge
```

## Step 6: Configure Legit Application

Update `src/main/resources/application.yaml`:

```yaml
blockchain:
  rpcUrl: "http://localhost:8545"
  privateKey: "YOUR_VALIDATOR_PRIVATE_KEY"
  auditLogContract: "0xYOUR_AUDIT_LOG_ADDRESS"
  reputationContract: "0xYOUR_REPUTATION_ADDRESS"
```

## Step 7: Start Legit Application

```bash
./gradlew run
```

Expected logs:
```
Connected to blockchain. Chain ID: 1337, Wallet: 0x...
BlockchainService initialized successfully — Polygon Amoy LIVE mode
```

## Step 8: Test the Integration

### 1. Check Health
```bash
curl http://localhost:8080/api/v1/gateway/health | jq '.data.services.blockchain'
```

Expected:
```json
{
  "name": "Blockchain",
  "status": "UP",
  "message": "Polygon Amoy testnet connected"
}
```

### 2. Upload a Document (triggers blockchain anchor)

```bash
# First, register and login to get JWT token
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test@1234",
    "username": "testuser",
    "fullName": "Test User"
  }'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test@1234"
  }' | jq -r '.data.accessToken')

# Upload document
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "AADHAAR_CARD",
    "documentNumber": "123456789012",
    "documentName": "Test Aadhaar Card",
    "rawData": "dGVzdCBkYXRhIGZvciBlbmNyeXB0aW9u",
    "metadata": {
      "fullName": "Test User",
      "dateOfBirth": "1990-01-01"
    }
  }'
```

Check Legit logs for:
```
Blockchain: Document anchored with hash 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
```

### 3. Check Blockchain Transaction

```bash
# Query the latest block
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "eth_getBlockByNumber",
    "params": ["latest", true],
    "id": 1
  }' | jq
```

## Common Polygon Edge Commands

### Check if network is running
```bash
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'
```

### Check account balance
```bash
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "eth_getBalance",
    "params": ["0xYourAddress", "latest"],
    "id": 1
  }'
```

### Monitor transactions
```bash
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "eth_getTransactionReceipt",
    "params": ["0xTransactionHash"],
    "id": 1
  }'
```

## Troubleshooting

### "Connection refused" on port 8545
- Start Polygon Edge server first
- Check if port 8545 is available: `lsof -i :8545`
- Make sure `--jsonrpc :8545` flag is set

### "Invalid chain ID"
- Check genesis file: `cat genesis.json | jq .params.chainID`
- Update application.yaml with correct chain ID
- Restart Legit application

### "Insufficient funds for gas"
- Check validator balance
- Premine more tokens in genesis
- Or send tokens to your account

### Nonce issues
- Restart Polygon Edge node to reset state
- Or wait for pending transactions to clear

## Production Deployment

For production with Polygon Edge:

1. **Remove `--insecure` flag** - use secure secrets storage
2. **Set up at least 4 validators** - for proper consensus
3. **Configure firewall rules** - limit RPC access
4. **Enable TLS** - for RPC endpoints
5. **Set up monitoring** - track node health and sync status
6. **Backup validator keys** - store securely
7. **Configure gas prices** - appropriate for your network

## Next Steps

1. ✅ Start Polygon Edge network
2. ✅ Deploy smart contracts
3. ✅ Update application.yaml with contract addresses
4. ✅ Restart Legit application
5. ✅ Test document upload → blockchain anchoring
6. ✅ Test verification → reputation tracking

Your blockchain integration is ready! The Legit app will now log all operations to your private Polygon Edge blockchain.
