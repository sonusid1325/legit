# Smart Contract Deployment Guide

## Prerequisites

1. **Get Polygon Amoy MATIC tokens**
   - Visit: https://faucet.polygon.technology/
   - Select "Polygon Amoy" network
   - Enter your wallet address
   - Request test MATIC tokens (0.2 MATIC should be enough)

2. **Set up Remix IDE** (easiest option)
   - Visit: https://remix.ethereum.org/
   - Or use Hardhat/Foundry for local deployment

## Option 1: Deploy with Remix (Recommended)

### Step 1: Connect to Polygon Amoy

1. Open MetaMask
2. Add Polygon Amoy network:
   - Network Name: `Polygon Amoy Testnet`
   - RPC URL: `https://rpc-amoy.polygon.technology`
   - Chain ID: `80002`
   - Currency Symbol: `MATIC`
   - Block Explorer: `https://www.oklink.com/amoy`

### Step 2: Deploy LegitAuditLog

1. Open Remix: https://remix.ethereum.org/
2. Create new file: `LegitAuditLog.sol`
3. Copy the contract code from `contracts/LegitAuditLog.sol`
4. Go to "Solidity Compiler" tab
5. Select compiler version: `0.8.20+`
6. Click "Compile LegitAuditLog.sol"
7. Go to "Deploy & Run Transactions" tab
8. Select Environment: "Injected Provider - MetaMask"
9. Ensure MetaMask is connected to Polygon Amoy
10. Click "Deploy"
11. Confirm transaction in MetaMask
12. **Copy the deployed contract address** (you'll need this!)

### Step 3: Deploy VerifierReputation

1. In Remix, create new file: `VerifierReputation.sol`
2. Copy the contract code from `contracts/VerifierReputation.sol`
3. Compile with `0.8.20+`
4. Deploy (same as above)
5. **Copy the deployed contract address**

### Step 4: Configure Legit Backend

1. Edit `src/main/resources/application.yaml`:

```yaml
blockchain:
  rpcUrl: "https://rpc-amoy.polygon.technology"
  privateKey: "YOUR_WALLET_PRIVATE_KEY_HERE"  # Get from MetaMask: Settings > Security & Privacy > Show private key
  auditLogContract: "0xYOUR_AUDIT_LOG_CONTRACT_ADDRESS"
  reputationContract: "0xYOUR_REPUTATION_CONTRACT_ADDRESS"
```

2. Restart the Legit application:
```bash
./gradlew run
```

3. Verify blockchain is connected:
```bash
curl http://localhost:8080/api/v1/gateway/health | jq '.data.services.blockchain'
```

Expected output:
```json
{
  "name": "Blockchain",
  "status": "UP",
  "message": "Polygon Amoy testnet connected"
}
```

## Option 2: Deploy with Hardhat

### Setup

```bash
npm init -y
npm install --save-dev hardhat @nomicfoundation/hardhat-toolbox
npx hardhat
```

### hardhat.config.js

```javascript
require("@nomicfoundation/hardhat-toolbox");

module.exports = {
  solidity: "0.8.20",
  networks: {
    amoy: {
      url: "https://rpc-amoy.polygon.technology",
      accounts: ["YOUR_PRIVATE_KEY_HERE"],
      chainId: 80002
    }
  }
};
```

### Deploy script (scripts/deploy.js)

```javascript
async function main() {
  console.log("Deploying LegitAuditLog...");
  const AuditLog = await ethers.getContractFactory("LegitAuditLog");
  const auditLog = await AuditLog.deploy();
  await auditLog.waitForDeployment();
  console.log("LegitAuditLog deployed to:", await auditLog.getAddress());

  console.log("Deploying VerifierReputation...");
  const Reputation = await ethers.getContractFactory("VerifierReputation");
  const reputation = await Reputation.deploy();
  await reputation.waitForDeployment();
  console.log("VerifierReputation deployed to:", await reputation.getAddress());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
```

### Deploy

```bash
npx hardhat run scripts/deploy.js --network amoy
```

## Verification on Block Explorer

After deployment, verify your contracts on OKLink:

1. Visit: https://www.oklink.com/amoy
2. Search for your contract address
3. View transactions and contract state

## Test the Integration

### 1. Upload a Document

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "AADHAAR_CARD",
    "documentNumber": "123456789012",
    "documentName": "Test Aadhaar",
    "rawData": "encrypted_data_here",
    "metadata": {
      "fullName": "Test User"
    }
  }'
```

**Expected:** Document hash anchored on blockchain. Check logs for:
```
Blockchain: Document anchored with hash <hash>
```

### 2. Create and Approve Verification Contract

```bash
# Create contract
curl -X POST http://localhost:8080/api/v1/pipeline/contracts \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{...}'

# Approve contract (triggers verification)
curl -X POST http://localhost:8080/api/v1/pipeline/contracts/approve \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "contractId": "CONTRACT_ID",
    "approved": true
  }'
```

**Expected:** Verification logged and reputation updated. Check logs for:
```
Blockchain: logVerification sent for contract <id> (passed=true)
Blockchain: Reputation updated for verifier <id> (success=true, rejected=false)
```

### 3. View on Blockchain

```bash
# Check transaction on OKLink
https://www.oklink.com/amoy/tx/<transaction_hash>
```

## Troubleshooting

### "Insufficient funds"
- Get more test MATIC from the faucet
- Each transaction costs ~0.001-0.01 MATIC

### "Nonce too high"
- Reset MetaMask account: Settings > Advanced > Reset Account

### "Invalid contract address"
- Ensure you copied the full address (starts with 0x)
- Verify the address is checksummed correctly

### Blockchain calls timing out
- Check RPC endpoint is responsive: `curl https://rpc-amoy.polygon.technology`
- Try alternative RPC: `https://polygon-amoy.g.alchemy.com/v2/demo`

## Cost Estimates

- Deploy LegitAuditLog: ~0.01 MATIC
- Deploy VerifierReputation: ~0.008 MATIC
- logVerification call: ~0.001 MATIC
- anchorDocument call: ~0.0008 MATIC
- updateReputation call: ~0.001 MATIC

**Total for deployment + 100 transactions: ~0.15 MATIC**

## Production Considerations

1. **Use environment variables for private key:**
   ```yaml
   blockchain:
     privateKey: "${BLOCKCHAIN_PRIVATE_KEY}"
   ```

2. **Monitor gas prices:**
   - Implement gas price oracle
   - Set max gas price limits

3. **Add transaction queuing:**
   - Handle nonce management
   - Retry failed transactions

4. **Set up monitoring:**
   - Track wallet balance
   - Alert on low funds
   - Monitor transaction success rate

5. **Backup strategy:**
   - Keep wallet private key secure
   - Export contract ABIs
   - Document all deployed addresses
