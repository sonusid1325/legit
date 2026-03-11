package com.sonusid.legit.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * The core of Legit's innovation:
 *
 * Instead of sharing documents, a service provider creates a VerificationContract.
 * The user approves it. A disposable key is generated. The verification happens
 * on OUR server — the service provider only gets a YES/NO result + the contract proof.
 * No document ever leaves the system.
 */

@Serializable
enum class ContractStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    VERIFICATION_IN_PROGRESS,
    VERIFIED,
    FAILED,
    EXPIRED,
    REVOKED
}

@Serializable
enum class VerificationField {
    FULL_NAME,
    DATE_OF_BIRTH,
    ADDRESS,
    GENDER,
    FATHER_NAME,
    DOCUMENT_VALIDITY,
    DOCUMENT_NUMBER_MATCH,
    IDENTITY_PROOF,
    ADDRESS_PROOF,
    AGE_VERIFICATION
}

@Serializable
data class VerificationContract(
    @SerialName("_id")
    @Contextual
    val id: ObjectId? = null,

    /** The service provider (company) requesting verification */
    val requesterId: String,
    val requesterName: String,

    /** The user whose documents are being verified */
    val userId: String,

    /** What documents are needed for this verification */
    val requiredDocumentTypes: List<DocumentType>,

    /** What specific fields need to be verified — NOT the actual data, just the check */
    val requiredFields: List<VerificationField>,

    /** A human-readable purpose so the user knows WHY */
    val purpose: String,

    /** Current status of this contract */
    val status: ContractStatus = ContractStatus.PENDING_APPROVAL,

    /** The disposable key — generated on approval, expires after use or timeout */
    val disposableKey: String? = null,

    /** When the disposable key expires (epoch millis). Short-lived by design. */
    val keyExpiresAt: Long? = null,

    /** The final verification result — this is ALL the service provider gets */
    val verificationResult: VerificationResult? = null,

    /** Audit trail */
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val verifiedAt: Long? = null,
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24hr to approve
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class VerificationResult(
    /** Each field check: did it pass or fail? No actual data exposed. */
    val fieldResults: Map<String, FieldVerificationResult>,

    /** Overall: is the user verified for this contract's purpose? */
    val overallStatus: OverallVerificationStatus,

    /** Cryptographic proof that verification happened on our server */
    val proofHash: String,

    /** Timestamp of when verification was performed */
    val verifiedAt: Long = System.currentTimeMillis(),

    /** A signed token the service provider can store as proof of verification */
    val verificationToken: String
)

@Serializable
data class FieldVerificationResult(
    val field: VerificationField,
    val verified: Boolean,
    val message: String? = null
)

@Serializable
enum class OverallVerificationStatus {
    PASS,
    FAIL,
    PARTIAL
}

// ========================
// REQUEST / RESPONSE DTOs
// ========================

/** Service provider sends this to request verification of a user */
@Serializable
data class CreateContractRequest(
    val userId: String,
    val requiredDocumentTypes: List<DocumentType>,
    val requiredFields: List<VerificationField>,
    val purpose: String
)

/** User approves or rejects the contract */
@Serializable
data class ContractApprovalRequest(
    val contractId: String,
    val approved: Boolean
)

/** Service provider uses the disposable key to trigger verification */
@Serializable
data class ExecuteVerificationRequest(
    val contractId: String,
    val disposableKey: String
)

/** What the service provider gets back — verified or not, no documents */
@Serializable
data class VerificationResponse(
    val contractId: String,
    val requesterName: String,
    val userId: String,
    val purpose: String,
    val status: ContractStatus,
    val result: VerificationResult?,
    val createdAt: Long,
    val verifiedAt: Long?
)

/** What the user sees — their pending/past contracts */
@Serializable
data class ContractSummary(
    val contractId: String,
    val requesterName: String,
    val purpose: String,
    val requiredDocumentTypes: List<DocumentType>,
    val requiredFields: List<VerificationField>,
    val status: ContractStatus,
    val createdAt: Long,
    val expiresAt: Long
)

/** List of contracts for a user */
@Serializable
data class ContractListResponse(
    val contracts: List<ContractSummary>,
    val total: Int
)

/** After approval — the disposable key info sent back to service provider */
@Serializable
data class DisposableKeyResponse(
    val contractId: String,
    val disposableKey: String,
    val expiresAt: Long,
    val message: String = "Use this key within the expiry window to execute verification. Key is single-use."
)

// ========================
// CONVERSION HELPERS
// ========================

fun VerificationContract.toResponse(): VerificationResponse = VerificationResponse(
    contractId = id?.toHexString() ?: "",
    requesterName = requesterName,
    userId = userId,
    purpose = purpose,
    status = status,
    result = verificationResult,
    createdAt = createdAt,
    verifiedAt = verifiedAt
)

fun VerificationContract.toSummary(): ContractSummary = ContractSummary(
    contractId = id?.toHexString() ?: "",
    requesterName = requesterName,
    purpose = purpose,
    requiredDocumentTypes = requiredDocumentTypes,
    requiredFields = requiredFields,
    status = status,
    createdAt = createdAt,
    expiresAt = expiresAt
)
