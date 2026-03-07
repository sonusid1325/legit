package com.sonusid.legit.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
enum class DocumentType {
    @SerialName("aadhaar_card")
    AADHAAR_CARD,

    @SerialName("pan_card")
    PAN_CARD,

    @SerialName("passport")
    PASSPORT,

    @SerialName("driving_license")
    DRIVING_LICENSE,

    @SerialName("voter_id")
    VOTER_ID,

    @SerialName("bank_statement")
    BANK_STATEMENT,

    @SerialName("address_proof")
    ADDRESS_PROOF,

    @SerialName("income_proof")
    INCOME_PROOF,

    @SerialName("education_certificate")
    EDUCATION_CERTIFICATE,

    @SerialName("other")
    OTHER
}

@Serializable
enum class DocumentStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("verified")
    VERIFIED,

    @SerialName("rejected")
    REJECTED,

    @SerialName("expired")
    EXPIRED
}

@Serializable
data class Document(
    @SerialName("_id")
    @Contextual
    val id: ObjectId? = null,
    val userId: String,
    val documentType: DocumentType,
    val documentNumber: String,
    val documentName: String,
    val metadata: DocumentMetadata,
    val dataHash: String,
    val encryptedData: String,
    val status: DocumentStatus = DocumentStatus.PENDING,
    val issuedBy: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class DocumentMetadata(
    val fullName: String? = null,
    val dateOfBirth: String? = null,
    val address: String? = null,
    val fatherName: String? = null,
    val gender: String? = null,
    val extraFields: Map<String, String> = emptyMap()
)

@Serializable
data class DocumentUploadRequest(
    val documentType: DocumentType,
    val documentNumber: String,
    val documentName: String,
    val metadata: DocumentMetadata,
    val rawData: String,
    val issuedBy: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null
)

@Serializable
data class DocumentResponse(
    val id: String,
    val userId: String,
    val documentType: DocumentType,
    val documentNumber: String,
    val documentName: String,
    val metadata: DocumentMetadata,
    val status: DocumentStatus,
    val issuedBy: String?,
    val issuedAt: Long?,
    val expiresAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class DocumentListResponse(
    val documents: List<DocumentSummary>,
    val total: Int
)

@Serializable
data class DocumentSummary(
    val id: String,
    val documentType: DocumentType,
    val documentName: String,
    val documentNumberMasked: String,
    val status: DocumentStatus,
    val createdAt: Long
)

fun Document.toResponse(): DocumentResponse = DocumentResponse(
    id = id?.toHexString() ?: "",
    userId = userId,
    documentType = documentType,
    documentNumber = maskDocumentNumber(documentNumber, documentType),
    documentName = documentName,
    metadata = metadata,
    status = status,
    issuedBy = issuedBy,
    issuedAt = issuedAt,
    expiresAt = expiresAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Document.toSummary(): DocumentSummary = DocumentSummary(
    id = id?.toHexString() ?: "",
    documentType = documentType,
    documentName = documentName,
    documentNumberMasked = maskDocumentNumber(documentNumber, documentType),
    status = status,
    createdAt = createdAt
)

fun maskDocumentNumber(number: String, type: DocumentType): String {
    if (number.length <= 4) return "****"
    return when (type) {
        DocumentType.AADHAAR_CARD -> "XXXX-XXXX-" + number.takeLast(4)
        DocumentType.PAN_CARD -> number.take(2) + "XXXXX" + number.takeLast(3)
        else -> "*".repeat(number.length - 4) + number.takeLast(4)
    }
}
