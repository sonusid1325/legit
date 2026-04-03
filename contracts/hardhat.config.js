import "@nomicfoundation/hardhat-ethers";

const config = {
  solidity: "0.8.20",
  networks: {
    edge: {
      type: 'http',
      url: "http://127.0.0.1:8545",
      chainId: 1337,
      accounts: process.env.PRIVATE_KEY ? [process.env.PRIVATE_KEY] : []
    }
  }
};

export default config;
