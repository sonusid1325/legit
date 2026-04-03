import hre from "hardhat";

async function main() {
  console.log("🚀 Deploying contracts to blockchain...\n");
  
  console.log("Deploying LegitAuditLog...");
  const AuditLog = await hre.ethers.getContractFactory("LegitAuditLog");
  const auditLog = await AuditLog.deploy();
  await auditLog.waitForDeployment();
  const auditAddress = await auditLog.getAddress();
  console.log("✅ LegitAuditLog deployed to:", auditAddress);

  console.log("\nDeploying VerifierReputation...");
  const Reputation = await hre.ethers.getContractFactory("VerifierReputation");
  const reputation = await Reputation.deploy();
  await reputation.waitForDeployment();
  const reputationAddress = await reputation.getAddress();
  console.log("✅ VerifierReputation deployed to:", reputationAddress);
  
  // Save addresses
  const fs = await import('fs');
  fs.writeFileSync('deployed-addresses.json', JSON.stringify({
    auditLog: auditAddress,
    reputation: reputationAddress,
    deployedAt: new Date().toISOString()
  }, null, 2));
  
  console.log("\n📋 Deployment Summary:");
  console.log("════════════════════════════════════════");
  console.log("AuditLog Contract:     ", auditAddress);
  console.log("Reputation Contract:   ", reputationAddress);
  console.log("════════════════════════════════════════\n");
  console.log("✅ Addresses saved to deployed-addresses.json");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
