package com.sonusid.legit.services

import org.slf4j.LoggerFactory
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Utf8String
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger
import java.security.MessageDigest

/**
 * BlockchainService — Blockchain Integration
 *
 * Provides audit logging and verifier reputation tracking on blockchain.
 * Supports Polygon Edge (local), Polygon Amoy (testnet), or Polygon mainnet.
 * All blockchain operations are non-fatal and fire-and-forget to ensure the main
 * application flow is never blocked or disrupted.
 *
 * If blockchain is not configured (privateKey is blank), the service operates in
 * SIMULATION mode, logging operations locally without hitting the blockchain.
 */
object BlockchainService {
    private val logger = LoggerFactory.getLogger(BlockchainService::class.java)

    private var web3j: Web3j? = null
    private var credentials: Credentials? = null
    private var auditLogAddress: String = ""
    private var reputationAddress: String = ""
    private var initialized: Boolean = false

    /**
     * Initialize the blockchain service with blockchain configuration.
     * This should be called once at application startup.
     *
     * @param rpcUrl The RPC endpoint (e.g., http://localhost:8545 for Polygon Edge)
     * @param privateKey The wallet private key (without 0x prefix)
     * @param auditLogContract The deployed LegitAuditLog contract address
     * @param reputationContract The deployed VerifierReputation contract address
     */
    fun init(rpcUrl: String, privateKey: String, auditLogContract: String, reputationContract: String) {
        try {
            // Only initialize if all critical parameters are provided
            if (privateKey.isBlank() || auditLogContract.isBlank() || reputationContract.isBlank()) {
                logger.warn("Blockchain configuration incomplete — running in SIMULATION mode")
                logger.info("To enable blockchain: set blockchain.privateKey, blockchain.auditLogContract, and blockchain.reputationContract in application.yaml")
                initialized = false
                return
            }

            // Connect to blockchain via Web3j
            web3j = Web3j.build(HttpService(rpcUrl))

            // Create credentials from private key
            val cleanPrivateKey = privateKey.removePrefix("0x")
            credentials = Credentials.create(cleanPrivateKey)

            // Store contract addresses
            auditLogAddress = auditLogContract
            reputationAddress = reputationContract

            // Test connection by fetching chain ID
            val chainId = web3j?.ethChainId()?.send()?.chainId
            logger.info("Connected to blockchain. Chain ID: {}, Wallet: {}", chainId, credentials?.address)

            initialized = true
            logger.info("BlockchainService initialized successfully — Blockchain LIVE mode")

        } catch (e: Exception) {
            logger.warn("Failed to initialize BlockchainService: ${e.message}. Running in SIMULATION mode.", e)
            initialized = false
            web3j = null
            credentials = null
        }
    }

    /**
     * Check if the blockchain service is initialized and connected.
     *
     * @return true if connected to blockchain, false if in simulation mode
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Log a verification event on the blockchain.
     * Calls the LegitAuditLog contract's logVerification function.
     *
     * @param contractId The internal contract ID (will be hashed to bytes32)
     * @param overallStatus The verification status string
     * @param passed Whether the verification passed
     */
    fun logVerification(contractId: String, overallStatus: String, passed: Boolean) {
        if (!initialized) {
            logger.info("BLOCKCHAIN SIM: logVerification contractId=$contractId status=$overallStatus passed=$passed")
            return
        }

        try {
            val contractHash = sha256ToBytes32(contractId)

            val function = Function(
                "logVerification",
                listOf(
                    Bytes32(contractHash),
                    Bool(passed),
                    Utf8String(overallStatus)
                ),
                emptyList()
            )

            sendTransaction(auditLogAddress, function)
            logger.info("Blockchain: logVerification sent for contract $contractId (passed=$passed)")

        } catch (e: Exception) {
            logger.warn("Blockchain logVerification failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Anchor a document hash on the blockchain.
     * Calls the LegitAuditLog contract's anchorDocument function.
     *
     * @param dataHash The SHA-256 hash of the document (hex string)
     */
    fun anchorDocument(dataHash: String) {
        if (!initialized) {
            logger.info("BLOCKCHAIN SIM: anchorDocument dataHash=$dataHash")
            return
        }

        try {
            val hashBytes = hexToBytes32(dataHash)

            val function = Function(
                "anchorDocument",
                listOf(Bytes32(hashBytes)),
                emptyList()
            )

            sendTransaction(auditLogAddress, function)
            logger.info("Blockchain: Document anchored with hash $dataHash")

        } catch (e: Exception) {
            logger.warn("Blockchain anchorDocument failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Log a disposable key burn event on the blockchain.
     * Calls the LegitAuditLog contract's logKeyBurn function.
     *
     * @param contractId The internal contract ID (will be hashed to bytes32)
     */
    fun logKeyBurn(contractId: String) {
        if (!initialized) {
            logger.info("BLOCKCHAIN SIM: logKeyBurn contractId=$contractId")
            return
        }

        try {
            val contractHash = sha256ToBytes32(contractId)

            val function = Function(
                "logKeyBurn",
                listOf(Bytes32(contractHash)),
                emptyList()
            )

            sendTransaction(auditLogAddress, function)
            logger.info("Blockchain: Key burn logged for contract $contractId")

        } catch (e: Exception) {
            logger.warn("Blockchain logKeyBurn failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Update verifier reputation on the blockchain.
     * Calls the VerifierReputation contract's updateReputation function.
     *
     * @param verifierAddress The verifier's user ID (will be hashed to bytes32)
     * @param success Whether the verification was successful
     * @param userRejected Whether the user rejected the verification request
     */
    fun updateReputation(verifierAddress: String, success: Boolean, userRejected: Boolean = false) {
        if (!initialized) {
            logger.info("BLOCKCHAIN SIM: updateReputation verifierId=$verifierAddress success=$success userRejected=$userRejected")
            return
        }

        try {
            val verifierIdHash = sha256ToBytes32(verifierAddress)

            val function = Function(
                "updateReputation",
                listOf(
                    Bytes32(verifierIdHash),
                    Bool(success),
                    Bool(userRejected)
                ),
                emptyList()
            )

            sendTransaction(reputationAddress, function)
            logger.info("Blockchain: Reputation updated for verifier $verifierAddress (success=$success, rejected=$userRejected)")

        } catch (e: Exception) {
            logger.warn("Blockchain updateReputation failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Send a transaction to the blockchain.
     * This is a fire-and-forget operation that does not wait for confirmation.
     *
     * @param contractAddress The smart contract address
     * @param function The Web3j Function to encode and send
     */
    private fun sendTransaction(contractAddress: String, function: Function) {
        val w3j = web3j ?: throw IllegalStateException("Web3j not initialized")
        val creds = credentials ?: throw IllegalStateException("Credentials not initialized")

        val encodedFunction = FunctionEncoder.encode(function)

        // Get current gas price
        val gasPrice = w3j.ethGasPrice().send().gasPrice

        // Get nonce
        val nonce = w3j.ethGetTransactionCount(
            creds.address,
            DefaultBlockParameterName.PENDING
        ).send().transactionCount

        // Create raw transaction
        val rawTransaction = org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
            creds.address,
            nonce,
            gasPrice,
            BigInteger.valueOf(300000), // Gas limit
            contractAddress,
            encodedFunction
        )

        // Sign and send transaction
        val transactionManager = RawTransactionManager(w3j, creds, 80002) // Polygon Amoy chain ID
        val ethSendTransaction = transactionManager.sendTransaction(
            gasPrice,
            BigInteger.valueOf(300000),
            contractAddress,
            encodedFunction,
            BigInteger.ZERO
        )

        logger.debug("Transaction sent: ${ethSendTransaction.transactionHash}")
    }

    /**
     * Hash a string using SHA-256 and return as a 32-byte array suitable for bytes32.
     *
     * @param input The input string to hash
     * @return 32-byte hash as ByteArray
     */
    private fun sha256ToBytes32(input: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
    }

    /**
     * Convert a hex string to a 32-byte array, padding if necessary.
     *
     * @param hexString The hex string (with or without 0x prefix)
     * @return 32-byte array
     */
    private fun hexToBytes32(hexString: String): ByteArray {
        val cleanHex = hexString.removePrefix("0x")
        val bytes = cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        // Ensure exactly 32 bytes (pad with zeros if needed)
        return when {
            bytes.size == 32 -> bytes
            bytes.size < 32 -> {
                val padded = ByteArray(32)
                bytes.copyInto(padded, 32 - bytes.size)
                padded
            }
            else -> bytes.copyOfRange(0, 32)
        }
    }
}
