# 📊 HOW TO VIEW BLOCKCHAIN TRANSACTIONS

Your contracts are deployed locally on Ganache. Here are ALL the ways to view transactions:

## Method 1: Ganache Terminal (EASIEST)

You should already see transactions in the terminal where Ganache is running!

Look for output like:
```
eth_sendRawTransaction

  Transaction: 0xabc123...
  Contract created: 0xe78A0F7E598Cc8b0Bb87894B0F60dD2a88d6a8Ab
  Gas usage: 1234567
  Block number: 5
  Block time: Thu Apr 03 2026 08:16:09 GMT+0530
```

**Every time your backend writes to blockchain, you'll see it here in REAL-TIME!**

## Method 2: Using curl (View Specific Transactions)

### Get Latest Block Number
```bash
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' | jq
```

### Get Block Details (including all transactions)
```bash
# Replace "latest" with specific block number like "0x5" if needed
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["latest",true],"id":1}' | jq
```

### Get Transaction Receipt
```bash
# After you see a transaction hash in Ganache, use it here:
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["0xYOUR_TX_HASH"],"id":1}' | jq
```

### Get All Transactions in a Block
```bash
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["latest",true],"id":1}' \
  | jq '.result.transactions'
```

## Method 3: Install Ganache GUI (Visual Interface)

If you want a nice visual interface:

```bash
# Install Ganache UI
npm install -g ganache-ui
# OR download from: https://trufflesuite.com/ganache/

# Then connect it to your running Ganache:
# Settings → Server → Port: 8545
```

**Note**: You're already running Ganache CLI, so you can just view the terminal logs!

## Method 4: Watch Transactions in Real-Time

Create a simple transaction monitor:

```bash
# Save this as watch-blockchain.sh
cat > watch-blockchain.sh << 'WATCH'
#!/bin/bash
echo "Watching blockchain for new transactions..."
LAST_BLOCK=0

while true; do
    CURRENT=$(curl -s -X POST http://localhost:8545 \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
        | jq -r '.result' | xargs printf "%d\n")
    
    if [ "$CURRENT" != "$LAST_BLOCK" ]; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "NEW BLOCK: $CURRENT ($(date))"
        curl -s -X POST http://localhost:8545 \
            -H "Content-Type: application/json" \
            -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockByNumber\",\"params\":[\"0x$(printf '%x' $CURRENT)\",true],\"id\":1}" \
            | jq -r '.result | "Gas Used: \(.gasUsed)\nTransactions: \(.transactions | length)"'
        LAST_BLOCK=$CURRENT
    fi
    sleep 2
done
WATCH

chmod +x watch-blockchain.sh
./watch-blockchain.sh
```

## Method 5: Query Specific Contract Events

### View Document Anchoring Events
```bash
# Get logs from LegitAuditLog contract
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "method":"eth_getLogs",
    "params":[{
      "fromBlock":"0x0",
      "toBlock":"latest",
      "address":"0xe78A0F7E598Cc8b0Bb87894B0F60dD2a88d6a8Ab"
    }],
    "id":1
  }' | jq
```

### View Reputation Updates
```bash
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "method":"eth_getLogs",
    "params":[{
      "fromBlock":"0x0",
      "toBlock":"latest",
      "address":"0x5b1869D9A4C187F2EAa108f3062412ecf0526b24"
    }],
    "id":1
  }' | jq
```

## Method 6: Create a Simple Web Dashboard

```bash
cd /home/sonu/IdeaProjects/legit/contracts
cat > blockchain-viewer.html << 'HTML'
<!DOCTYPE html>
<html>
<head>
    <title>Legit Blockchain Viewer</title>
    <style>
        body { font-family: monospace; padding: 20px; background: #1e1e1e; color: #fff; }
        .block { border: 1px solid #4CAF50; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .tx { background: #2d2d2d; padding: 10px; margin: 5px 0; border-left: 3px solid #2196F3; }
        h2 { color: #4CAF50; }
        button { padding: 10px 20px; background: #4CAF50; color: white; border: none; cursor: pointer; }
    </style>
</head>
<body>
    <h1>🔗 Legit Blockchain Transaction Viewer</h1>
    <button onclick="refresh()">🔄 Refresh</button>
    <div id="status"></div>
    <div id="blocks"></div>
    
    <script>
        const RPC = 'http://localhost:8545';
        
        async function rpcCall(method, params = []) {
            const res = await fetch(RPC, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({jsonrpc: '2.0', method, params, id: 1})
            });
            return (await res.json()).result;
        }
        
        async function refresh() {
            const blockNum = parseInt(await rpcCall('eth_blockNumber'), 16);
            document.getElementById('status').innerHTML = 
                `<h2>Latest Block: ${blockNum}</h2>`;
            
            let html = '';
            for (let i = Math.max(0, blockNum - 10); i <= blockNum; i++) {
                const block = await rpcCall('eth_getBlockByNumber', [`0x${i.toString(16)}`, true]);
                if (!block) continue;
                
                html += `<div class="block">
                    <strong>Block ${i}</strong> | 
                    Gas: ${parseInt(block.gasUsed, 16)} | 
                    Transactions: ${block.transactions.length} |
                    Time: ${new Date(parseInt(block.timestamp, 16) * 1000).toLocaleString()}
                    <div>`;
                
                block.transactions.forEach(tx => {
                    html += `<div class="tx">
                        TX: ${tx.hash.slice(0, 20)}...
                        ${tx.to ? `→ ${tx.to.slice(0, 20)}...` : '(Contract Creation)'}
                    </div>`;
                });
                
                html += '</div></div>';
            }
            document.getElementById('blocks').innerHTML = html;
        }
        
        refresh();
        setInterval(refresh, 5000); // Auto-refresh every 5 seconds
    </script>
</body>
</html>
HTML

echo "✅ Created blockchain-viewer.html"
echo "Open it in browser: file://$(pwd)/blockchain-viewer.html"
```

## 🔥 LIVE TEST: Trigger a Transaction NOW

```bash
# Register and upload a document to see a blockchain transaction:
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@blockchain.com","password":"Test@1234"}' \
  | jq -r '.data.accessToken')

curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "PAN_CARD",
    "documentNumber": "ABCDE1234F",
    "documentName": "My PAN",
    "rawData": "dGVzdCBkYXRh",
    "metadata": {"fullName": "Test User"}
  }'

# NOW check your Ganache terminal - you'll see the transaction!
# OR run:
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["latest",true],"id":1}' \
  | jq '.result.transactions[-1]'  # Last transaction
```

## 📋 What Each Transaction Type Looks Like

### Document Anchoring
```json
{
  "to": "0xe78A0F7E598Cc8b0Bb87894B0F60dD2a88d6a8Ab",  // AuditLog contract
  "input": "0x..." // Contains the document hash
}
```

### Verification Logging
```json
{
  "to": "0xe78A0F7E598Cc8b0Bb87894B0F60dD2a88d6a8Ab",
  "input": "0x..." // Contains contract ID, pass/fail status
}
```

### Reputation Update
```json
{
  "to": "0x5b1869D9A4C187F2EAa108f3062412ecf0526b24",  // Reputation contract
  "input": "0x..." // Contains verifier ID, success flag
}
```

## 🎯 QUICK COMMANDS

```bash
# Show last 5 blocks with transactions
for i in {0..4}; do
  curl -s -X POST http://localhost:8545 \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["latest",true],"id":1}' \
    | jq ".result | {block: .number, txCount: (.transactions | length), gasUsed: .gasUsed}"
  sleep 1
done

# Count total transactions
curl -s -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
  | jq -r '.result' | xargs printf "Total blocks: %d\n"
```

---

**EASIEST METHOD**: Just watch your Ganache terminal where you started it!
Every blockchain transaction shows up there in real-time! 🎉

