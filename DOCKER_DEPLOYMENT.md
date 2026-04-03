# 🐳 Docker Deployment Guide

Complete containerized deployment of Legit KYC Platform with blockchain integration.

## 📦 What's Included

Your Docker setup includes 3 services:

1. **MongoDB** - Database for user data, documents, and contracts
2. **Blockchain** - Ganache with auto-deployed smart contracts
3. **Backend** - Legit Ktor application with blockchain integration

## 🚀 Quick Start (One Command)

```bash
# Build and start everything
docker-compose up -d

# Watch logs
docker-compose logs -f
```

That's it! Your entire stack is running with blockchain integration! 🎉

## 📋 Service Details

### MongoDB Service
- **Port**: 27017
- **Database**: legit
- **Volume**: Persistent storage for all data
- **Health Check**: Automatic ping check

### Blockchain Service
- **Port**: 8545
- **Type**: Ganache (local Ethereum blockchain)
- **Chain ID**: 1337
- **Features**:
  - ✅ Auto-starts on container launch
  - ✅ Auto-deploys smart contracts
  - ✅ 10 pre-funded accounts (1000 ETH each)
  - ✅ Deterministic addresses
  - ✅ Persistent blockchain data
- **Contracts**:
  - LegitAuditLog (deployed automatically)
  - VerifierReputation (deployed automatically)

### Backend Service
- **Port**: 8080
- **Dependencies**: Waits for MongoDB + Blockchain to be healthy
- **Features**:
  - ✅ Connects to blockchain automatically
  - ✅ Reads contract addresses from blockchain service
  - ✅ Full API functionality
  - ✅ Blockchain audit logging enabled

## 🔧 Detailed Commands

### Start Services
```bash
# Start all services
docker-compose up -d

# Start specific service
docker-compose up -d blockchain

# Build and start (if you changed code)
docker-compose up -d --build
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f blockchain
docker-compose logs -f backend

# Last 100 lines
docker-compose logs --tail=100 backend
```

### Stop Services
```bash
# Stop all
docker-compose down

# Stop and remove volumes (DELETES ALL DATA!)
docker-compose down -v

# Stop specific service
docker-compose stop backend
```

### Check Status
```bash
# List running containers
docker-compose ps

# Check health
docker-compose ps --services --filter "status=running"

# Inspect specific service
docker inspect legit-blockchain
```

### Access Services
```bash
# Execute command in container
docker-compose exec backend sh
docker-compose exec blockchain sh

# View blockchain deployment info
docker-compose exec blockchain cat /data/deployment.json

# Check MongoDB
docker-compose exec mongodb mongosh legit
```

## 🧪 Testing the Deployment

### 1. Check Health
```bash
curl http://localhost:8080/api/v1/gateway/health | jq
```

Expected response:
```json
{
  "success": true,
  "data": {
    "status": "HEALTHY",
    "services": {
      "mongodb": {"status": "UP"},
      "blockchain": {"status": "UP"}
    }
  }
}
```

### 2. Register User
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "docker@test.com",
    "password": "Test@1234",
    "username": "dockertest",
    "fullName": "Docker Test"
  }'
```

### 3. Upload Document (Creates Blockchain Transaction!)
```bash
# Login first
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"docker@test.com","password":"Test@1234"}' \
  | jq -r '.data.accessToken')

# Upload document
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "AADHAAR_CARD",
    "documentNumber": "123456789012",
    "documentName": "Test Doc",
    "rawData": "dGVzdA==",
    "metadata": {"fullName": "Docker Test"}
  }'

# Check blockchain logs
docker-compose logs blockchain | tail -20
```

### 4. View Blockchain Transactions
```bash
# Get latest block
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["latest",true],"id":1}' \
  | jq '.result.transactions'
```

## 📊 Monitoring

### View Real-time Logs
```bash
# All services in one view
docker-compose logs -f --tail=50

# Blockchain only
docker-compose logs -f blockchain

# Backend only
docker-compose logs -f backend
```

### Check Resource Usage
```bash
docker stats legit-backend legit-blockchain legit-mongodb
```

### View Deployed Contract Addresses
```bash
docker-compose exec blockchain cat /data/deployment.json | jq
```

## 🔄 Updating the Application

### After Code Changes
```bash
# Rebuild and restart backend
docker-compose up -d --build backend

# Rebuild all services
docker-compose up -d --build
```

### After Contract Changes
```bash
# Rebuild blockchain service (redeploys contracts)
docker-compose up -d --build blockchain

# Wait for deployment
docker-compose logs -f blockchain
```

## 💾 Data Persistence

All data is persisted in Docker volumes:

- `mongodb_data` - Database data
- `blockchain_data` - Blockchain state + contract addresses
- `backend_logs` - Application logs

### Backup Data
```bash
# Backup MongoDB
docker-compose exec mongodb mongodump --out=/data/backup
docker cp legit-mongodb:/data/backup ./mongodb-backup

# Backup blockchain
docker cp legit-blockchain:/data/deployment.json ./blockchain-deployment.json
```

### Restore Data
```bash
# Restore MongoDB
docker cp ./mongodb-backup legit-mongodb:/data/backup
docker-compose exec mongodb mongorestore /data/backup
```

## 🛠️ Troubleshooting

### Service Won't Start
```bash
# Check logs
docker-compose logs backend

# Check if ports are in use
lsof -i :8080  # Backend
lsof -i :8545  # Blockchain
lsof -i :27017 # MongoDB

# Remove and recreate
docker-compose down
docker-compose up -d
```

### Blockchain Not Deploying Contracts
```bash
# View blockchain logs
docker-compose logs blockchain

# Rebuild blockchain service
docker-compose up -d --build blockchain

# Check deployment file
docker-compose exec blockchain cat /data/deployment.json
```

### Backend Can't Connect to Blockchain
```bash
# Check if blockchain is healthy
docker-compose ps

# Test blockchain connection
docker-compose exec backend curl http://blockchain:8545

# Restart backend
docker-compose restart backend
```

### Reset Everything
```bash
# Stop and remove all data
docker-compose down -v

# Rebuild from scratch
docker-compose up -d --build
```

## 🌐 Production Deployment

### Environment Variables
Create a `.env` file:
```bash
# .env
MONGODB_URI=mongodb://mongodb:27017
JWT_SECRET=your-super-secret-jwt-key-here
ENCRYPTION_SECRET=your-encryption-secret-here
BLOCKCHAIN_PRIVATE_KEY=your-production-private-key
```

Then use it:
```bash
docker-compose --env-file .env up -d
```

### Security Hardening
```bash
# Use secrets for sensitive data
docker secret create jwt_secret ./jwt_secret.txt
docker secret create blockchain_key ./blockchain_key.txt

# Update docker-compose to use secrets
```

### Scale Backend
```bash
# Run multiple backend instances
docker-compose up -d --scale backend=3

# With load balancer (nginx)
# Add nginx service to docker-compose.yml
```

## 📈 Performance Tuning

### Increase Memory for Backend
Edit `docker-compose.yml`:
```yaml
services:
  backend:
    environment:
      JAVA_OPTS: "-Xmx1024m -Xms512m"
```

### Optimize Blockchain
```yaml
services:
  blockchain:
    environment:
      GANACHE_OPTS: "--blockTime 1"  # Faster blocks
```

## 🎯 Quick Reference

```bash
# Start everything
docker-compose up -d

# View logs
docker-compose logs -f

# Check status
docker-compose ps

# Stop everything
docker-compose down

# Rebuild after changes
docker-compose up -d --build

# View blockchain contracts
docker-compose exec blockchain cat /data/deployment.json

# Access MongoDB shell
docker-compose exec mongodb mongosh legit

# Test API
curl http://localhost:8080/api/v1/gateway/health

# View blockchain transactions
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'
```

## ✅ Success Indicators

Your deployment is working when:
- ✅ `docker-compose ps` shows all services as "Up (healthy)"
- ✅ Health check returns `"status": "HEALTHY"`
- ✅ Blockchain service has `/data/deployment.json`
- ✅ Document uploads create blockchain transactions
- ✅ Logs show "BlockchainService — Blockchain Audit Trail (LIVE)"

---

**Your entire Legit KYC platform is now containerized!** 🎉

Run `docker-compose up -d` and you're live with blockchain integration!

