#!/bin/bash

echo "🔗 LIVE BLOCKCHAIN TRANSACTION MONITOR"
echo "════════════════════════════════════════════════════════════"
echo "Watching http://localhost:8545 for new transactions..."
echo "Press Ctrl+C to stop"
echo ""

LAST_BLOCK=0

while true; do
    # Get current block number
    CURRENT=$(curl -s -X POST http://localhost:8545 \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
        | jq -r '.result' | xargs printf "%d\n" 2>/dev/null)
    
    if [ "$CURRENT" != "$LAST_BLOCK" ] && [ ! -z "$CURRENT" ]; then
        echo ""
        echo "🆕 NEW BLOCK DETECTED!"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "📦 Block Number: $CURRENT"
        echo "⏰ Time: $(date '+%Y-%m-%d %H:%M:%S')"
        
        # Get block details
        BLOCK_DATA=$(curl -s -X POST http://localhost:8545 \
            -H "Content-Type: application/json" \
            -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockByNumber\",\"params\":[\"0x$(printf '%x' $CURRENT)\",true],\"id\":1}")
        
        TX_COUNT=$(echo $BLOCK_DATA | jq -r '.result.transactions | length')
        GAS_USED=$(echo $BLOCK_DATA | jq -r '.result.gasUsed' | xargs printf "%d\n" 2>/dev/null)
        
        echo "📊 Transactions: $TX_COUNT"
        echo "⛽ Gas Used: $GAS_USED"
        
        if [ "$TX_COUNT" -gt "0" ]; then
            echo ""
            echo "📝 Transaction Details:"
            echo $BLOCK_DATA | jq -r '.result.transactions[] | 
                "  • Hash: \(.hash)
  • From: \(.from)
  • To: \(.to // "CONTRACT CREATION ✨")
  • Gas: \(.gas | tonumber)"'
        fi
        
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        LAST_BLOCK=$CURRENT
    else
        echo -ne "⏳ Waiting for new transactions... (Block: $CURRENT)\r"
    fi
    
    sleep 2
done
