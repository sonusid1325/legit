import { ethers } from 'ethers';
import solc from 'solc';
import fs from 'fs';
import path from 'path';

async function compileContract(contractName, sourceCode) {
    console.log(`\n📝 Compiling ${contractName}...`);
    
    const input = {
        language: 'Solidity',
        sources: {
            [`${contractName}.sol`]: {
                content: sourceCode
            }
        },
        settings: {
            outputSelection: {
                '*': {
                    '*': ['abi', 'evm.bytecode']
                }
            }
        }
    };

    const output = JSON.parse(solc.compile(JSON.stringify(input)));
    
    if (output.errors) {
        const errors = output.errors.filter(e => e.severity === 'error');
        if (errors.length > 0) {
            console.error('Compilation errors:', errors);
            throw new Error('Contract compilation failed');
        }
    }

    const contract = output.contracts[`${contractName}.sol`][contractName];
    console.log(`✅ ${contractName} compiled successfully`);
    
    return {
        abi: contract.abi,
        bytecode: contract.evm.bytecode.object
    };
}

async function main() {
    console.log("🚀 DEPLOYING CONTRACTS TO LOCAL GANACHE");
    console.log("═══════════════════════════════════════════════════════════\n");
    
    // Connect to Ganache
    const provider = new ethers.JsonRpcProvider("http://127.0.0.1:8545");
    const privateKey = "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d";
    const wallet = new ethers.Wallet(privateKey, provider);
    
    console.log("🔗 Connected to Ganache");
    console.log("   Deployer:", wallet.address);
    const balance = await provider.getBalance(wallet.address);
    console.log("   Balance:", ethers.formatEther(balance), "ETH");
    
    // Read contract source code
    const auditLogSource = fs.readFileSync('LegitAuditLog.sol', 'utf8');
    const reputationSource = fs.readFileSync('VerifierReputation.sol', 'utf8');
    
    // Compile contracts
    const auditLogCompiled = await compileContract('LegitAuditLog', auditLogSource);
    const reputationCompiled = await compileContract('VerifierReputation', reputationSource);
    
    // Deploy LegitAuditLog
    console.log("\n🚀 Deploying LegitAuditLog...");
    const AuditLogFactory = new ethers.ContractFactory(
        auditLogCompiled.abi,
        auditLogCompiled.bytecode,
        wallet
    );
    const auditLog = await AuditLogFactory.deploy();
    await auditLog.waitForDeployment();
    const auditLogAddress = await auditLog.getAddress();
    console.log("✅ LegitAuditLog deployed to:", auditLogAddress);
    
    // Deploy VerifierReputation
    console.log("\n🚀 Deploying VerifierReputation...");
    const ReputationFactory = new ethers.ContractFactory(
        reputationCompiled.abi,
        reputationCompiled.bytecode,
        wallet
    );
    const reputation = await ReputationFactory.deploy();
    await reputation.waitForDeployment();
    const reputationAddress = await reputation.getAddress();
    console.log("✅ VerifierReputation deployed to:", reputationAddress);
    
    // Save deployment info
    const deployment = {
        auditLogContract: auditLogAddress,
        reputationContract: reputationAddress,
        network: "Ganache Local",
        rpcUrl: "http://localhost:8545",
        chainId: 1337,
        privateKey: privateKey,
        deployedAt: new Date().toISOString(),
        deployer: wallet.address
    };
    
    fs.writeFileSync('deployment.json', JSON.stringify(deployment, null, 2));
    
    // Display summary
    console.log("\n═══════════════════════════════════════════════════════════");
    console.log("✅ DEPLOYMENT COMPLETE!");
    console.log("═══════════════════════════════════════════════════════════\n");
    console.log("📋 Contract Addresses:");
    console.log("   LegitAuditLog:        ", auditLogAddress);
    console.log("   VerifierReputation:   ", reputationAddress);
    console.log("\n📝 Update your application.yaml:");
    console.log("\nblockchain:");
    console.log(`  rpcUrl: "http://localhost:8545"`);
    console.log(`  privateKey: "${privateKey}"`);
    console.log(`  auditLogContract: "${auditLogAddress}"`);
    console.log(`  reputationContract: "${reputationAddress}"`);
    console.log("\n💾 Deployment info saved to: deployment.json");
    console.log("\n🚀 Next: Restart your Legit backend with: ./gradlew run");
    console.log("═══════════════════════════════════════════════════════════\n");
}

main().catch((error) => {
    console.error("\n❌ Deployment failed:", error);
    process.exit(1);
});
