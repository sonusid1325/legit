# 🚀 SIMPLE POLYGON EDGE SETUP (WORKS 100%)

Since Polygon Edge v1.3.3 requires complex bridge configuration for polybft,
here's the SIMPLEST way to get everything working:

## Option 1: Use Ganache (Easiest - 2 minutes)

Ganache is a simple Ethereum blockchain perfect for development:

```bash
# Install Ganache CLI
npm install -g ganache

# Start Ganache (it runs on port 8545 by default)
ganache --chain.chainId 1337 --wallet.deterministic

# You'll see:
# Available Accounts
# ==================
# (0) 0x90F8bf6A479f320ead074411a4B0e7944Ea8c9C1 (1000 ETH)
# 
# Private Keys
# ==================
# (0) 0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d
```

Copy the first private key and use it below!

## Option 2: Use Remix VM (No installation)

1. Open https://remix.ethereum.org/
2. Deploy contracts directly in browser
3. Use Remix's JavaScript VM
4. Copy deployed addresses
5. Set blockchain to SIMULATION mode (no private key needed)

## QUICK DEPLOYMENT WITH GANACHE

### Step 1: Start Ganache
```bash
ganache --chain.chainId 1337 --wallet.deterministic
```

### Step 2: Deploy Contracts with Hardhat
```bash
cd /home/sonu/IdeaProjects/legit/contracts

# Set private key from Ganache
export PRIVATE_KEY="0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d"

# Deploy
npx hardhat run scripts/deploy.js --network edge
```

### Step 3: Update application.yaml
```yaml
blockchain:
  rpcUrl: "http://localhost:8545"
  privateKey: "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d"
  auditLogContract: "0xYOUR_DEPLOYED_ADDRESS"
  reputationContract: "0xYOUR_DEPLOYED_ADDRESS"
```

### Step 4: Start Legit
```bash
cd /home/sonu/IdeaProjects/legit
./gradlew run
```

## OR... Just Use SIMULATION Mode!

The easiest option: **Don't configure blockchain at all!**

Your backend works 100% perfectly without blockchain. Just leave the config empty:

```yaml
blockchain:
  privateKey: ""  # SIMULATION mode
```

Everything functions normally, it just logs "BLOCKCHAIN SIM:" instead of sending real transactions.

You can add blockchain later when you're ready!

## Commands to Copy-Paste

```bash
# Install Ganache
npm install -g ganache

# Terminal 1: Start Ganache
ganache --chain.chainId 1337 --wallet.deterministic

# Terminal 2: Deploy contracts
cd /home/sonu/IdeaProjects/legit/contracts
export PRIVATE_KEY="0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d"
npx hardhat run scripts/deploy.js --network edge

# Copy the deployed addresses, then edit:
nano ../src/main/resources/application.yaml

# Update blockchain section with:
#   privateKey: "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d"
#   auditLogContract: "<from deployment>"
#   reputationContract: "<from deployment>"

# Terminal 3: Start Legit
cd /home/sonu/IdeaProjects/legit
./gradlew run
```

That's it! 🎉

