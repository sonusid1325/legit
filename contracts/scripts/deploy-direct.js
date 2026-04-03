import { ethers } from 'ethers';
import fs from 'fs';

async function main() {
  console.log("🚀 Deploying contracts directly with ethers.js...\n");
  
  // Connect to Ganache
  const provider = new ethers.JsonRpcProvider("http://127.0.0.1:8545");
  const privateKey = "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d";
  const wallet = new ethers.Wallet(privateKey, provider);
  
  console.log("Connected to blockchain");
  console.log("Deployer address:", wallet.address);
  const balance = await provider.getBalance(wallet.address);
  console.log("Balance:", ethers.formatEther(balance), "ETH\n");
  
  // Read contract ABIs
  const auditLogSource = fs.readFileSync('LegitAuditLog.sol', 'utf8');
  const reputationSource = fs.readFileSync('VerifierReputation.sol', 'utf8');
  
  console.log("⚠️  Direct deployment requires compiled contracts.");
  console.log("Please use Remix IDE instead:");
  console.log("1. Go to https://remix.ethereum.org/");
  console.log("2. Create LegitAuditLog.sol and VerifierReputation.sol");
  console.log("3. Compile with Solidity 0.8.20");
  console.log("4. Deploy to Injected Provider (MetaMask connected to http://localhost:8545)");
  console.log("5. Copy the deployed addresses");
}

main().catch(console.error);
