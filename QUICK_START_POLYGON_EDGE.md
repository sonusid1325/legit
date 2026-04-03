# 🚀 Quick Start: Polygon Edge + Legit Integration

## Prerequisites
- Polygon Edge installed and running
- MongoDB running on localhost:27017
- Java 25 and Gradle installed

## Step-by-Step Setup (5 minutes)

### 1. Start Polygon Edge (Single Node - Dev Mode)

```bash
# If genesis.json doesn't exist, create it
polygon-edge genesis \
  --consensus ibft \
  --ibft-validator 0x85da99c8a7c2c95964c8efd687e95e632fc533d6 \
  --premine 0x85da99c8a7c2c95964c8efd687e95e632fc533d6:1000000000000000000000000 \
  --chain-id 1337 \
  --block-gas-limit 10000000

# Start the server
polygon-edge server \
  --data-dir ./data-dir1 \
  --chain genesis.json \
  --grpc-address :10000 \
  --libp2p :30301 \
  --jsonrpc :8545 \
  --seal
```

Leave this running in Terminal 1.

### 2. Get Your Private Key

In Terminal 2:
```bash
# View validator key
cat data-dir1/consensus/validator.key
```

Copy the hex value (without any prefixes).

### 3. Deploy Smart Contracts

#### Quick Deploy with Remix:

1. Open https://remix.ethereum.org/
2. Create `LegitAuditLog.sol` (copy from `contracts/LegitAuditLog.sol`)
3. Create `VerifierReputation.sol` (copy from `contracts/VerifierReputation.sol`)
4. Compile both (Solidity 0.8.20)
5. In MetaMask:
   - Add network: `http://localhost:8545`, Chain ID: `1337`
   - Import account using your validator private key
6. Deploy both contracts in Remix
7. **Copy both deployed addresses**

### 4. Configure Legit

Edit `src/main/resources/application.yaml`:

```yaml
blockchain:
  rpcUrl: "http://localhost:8545"
  privateKey: "YOUR_VALIDATOR_PRIVATE_KEY_HERE"
  auditLogContract: "0xYOUR_AUDIT_LOG_CONTRACT_ADDRESS"
  reputationContract: "0xYOUR_REPUTATION_CONTRACT_ADDRESS"
```

### 5. Start Legit Application

In Terminal 3:
```bash
cd /home/sonu/IdeaProjects/legit
./gradlew run
```

Look for this in the logs:
```
✓ BlockchainService   — Polygon Amoy Audit Trail (LIVE)
Connected to blockchain. Chain ID: 1337, Wallet: 0x...
```

### 6. Test It!

In Terminal 4:

```bash
# Check health
curl http://localhost:8080/api/v1/gateway/health | jq '.data.services.blockchain'

# Should show:
# "status": "UP"
# "message": "Polygon Amoy testnet connected"
```

## What Gets Logged to Blockchain

✅ **Document Upload** → Hash anchored on-chain  
✅ **Verification Complete** → Result + proof logged  
✅ **User Rejects Contract** → Reputation penalty  
✅ **Disposable Key Burned** → Burn event recorded

## View Blockchain Activity

```bash
# Latest block
curl -s -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' | jq

# Should increment as you use the app!
```

## Troubleshooting

**Blockchain shows "SIMULATED":**
- Check `privateKey` is set in application.yaml
- Check contract addresses are set
- Restart Legit app after config changes

**"Connection refused":**
- Ensure Polygon Edge is running on port 8545
- Check: `curl http://localhost:8545`

**"Insufficient funds":**
- Your validator account needs tokens
- Check genesis premine settings
- Redeploy genesis with more premine

## All Green? You're Done! 🎉

Your Legit platform now has:
- ✅ Immutable audit trail on private blockchain
- ✅ Verifier reputation tracking
- ✅ Document hash anchoring
- ✅ Zero-downtime if blockchain is unavailable
- ✅ Complete transaction history

Check `BLOCKCHAIN_INTEGRATION.md` for full documentation.
