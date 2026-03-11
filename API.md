# 🔐 Legit API Reference

> Base URL: `http://localhost:8080`
> All authenticated endpoints require `Authorization: Bearer <token>` header.

---

## Table of Contents

- [Authentication](#authentication)
  - [Register](#register)
  - [Login](#login)
  - [Refresh Token](#refresh-token)
  - [Logout](#logout)
- [User Management](#user-management)
  - [Get Profile](#get-profile)
  - [Update Profile](#update-profile)
  - [Change Password](#change-password)
  - [Get Session](#get-session)
- [Document Vault](#document-vault)
  - [Upload Document](#upload-document)
  - [List All Documents](#list-all-documents)
  - [Get Document by ID](#get-document-by-id)
  - [Filter by Type](#filter-documents-by-type)
  - [Update Document Status](#update-document-status)
  - [Delete Document](#delete-document)
  - [Verify Integrity](#verify-document-integrity)
  - [Supported Types](#supported-document-types)
- [Verification Pipeline — User](#verification-pipeline--user)
  - [List All Contracts](#list-all-contracts)
  - [List Pending Contracts](#list-pending-contracts)
  - [Approve/Reject Contract](#approvereject-contract)
  - [View Contract Details](#view-contract-details)
  - [Revoke Contract](#revoke-contract)
  - [View History](#view-verification-history)
- [Verification Pipeline — Service Provider](#verification-pipeline--service-provider)
  - [Create Contract](#create-contract)
  - [Execute Verification](#execute-verification)
  - [List Requester Contracts](#list-requester-contracts)
  - [Get Verification Result](#get-verification-result)
- [Pipeline Admin](#pipeline-admin)
  - [Cleanup Expired Contracts](#cleanup-expired-contracts)
  - [Admin View User Contracts](#admin-view-user-contracts)
  - [Admin View Requester Contracts](#admin-view-requester-contracts)
  - [Admin View Contract Details](#admin-view-contract-details)
- [Document Admin](#document-admin)
  - [Admin Update Document Status](#admin-update-document-status)
  - [Admin View User Documents](#admin-view-user-documents)
- [Pipeline Info (Public)](#pipeline-info-public)
  - [How It Works](#how-it-works)
  - [Verification Fields](#verification-fields)
  - [Contract Statuses](#contract-statuses)
- [Gateway](#gateway)
  - [Health Check](#health-check)
  - [API Info](#api-info)
  - [Endpoints List](#endpoints-list)
  - [Uptime](#uptime)
  - [Ping](#ping)
- [Enums & Constants](#enums--constants)
- [Error Codes](#error-codes)
- [Android Integration Notes](#android-integration-notes)

---

## Authentication

### Register

Create a new user account.

```
POST /api/v1/auth/register
Content-Type: application/json
```

**Request Body:**
```json
{
    "username": "sonu",
    "email": "sonu@legit.dev",
    "password": "Legit@2026!",
    "fullName": "Sonu Kumar",
    "phoneNumber": "+919876543210"
}
```

**Validation Rules:**
- `username`: 3-30 chars, alphanumeric + `_.-` only
- `email`: valid email format
- `password`: min 8 chars, at least 1 uppercase, 1 lowercase, 1 digit, 1 special char
- `fullName`: min 2 chars
- `phoneNumber`: optional, 10-15 digits, can start with `+`

**Success Response (201):**
```json
{
    "success": true,
    "message": "Account created successfully",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
        "userId": "69b1916f12c7fe019f8d1485",
        "username": "sonu",
        "role": "USER",
        "expiresIn": 3600
    },
    "timestamp": 1773244783500
}
```

**Error Response — Email Exists (409):**
```json
{
    "code": "USER_004",
    "message": "An account with this email already exists",
    "details": {},
    "timestamp": 1773244801710
}
```

**Error Response — Validation Failed (422):**
```json
{
    "code": "GEN_002",
    "message": "Validation failed",
    "details": {
        "username": "Username is required",
        "email": "Invalid email format",
        "password": "Password must contain at least one special character",
        "fullName": "Full name is required"
    },
    "timestamp": 1773244801798
}
```

---

### Login

Authenticate with email and password.

```
POST /api/v1/auth/login
Content-Type: application/json
```

**Request Body:**
```json
{
    "email": "sonu@legit.dev",
    "password": "Legit@2026!"
}
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Login successful",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
        "userId": "69b1916f12c7fe019f8d1485",
        "username": "sonu",
        "role": "USER",
        "expiresIn": 3600
    },
    "timestamp": 1773244802431
}
```

**Error Response — Invalid Credentials (401):**
```json
{
    "code": "AUTH_001",
    "message": "Invalid email or password",
    "details": {},
    "timestamp": 1773244802960
}
```

---

### Refresh Token

Exchange a refresh token for a new access token.

```
POST /api/v1/auth/refresh
Content-Type: application/json
```

**Request Body:**
```json
{
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Token refreshed successfully",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
        "userId": "69b1916f12c7fe019f8d1485",
        "username": "sonu",
        "role": "USER",
        "expiresIn": 3600
    },
    "timestamp": 1773244803107
}
```

**Error Response — Expired/Invalid (401):**
```json
{
    "code": "AUTH_002",
    "message": "Invalid or expired refresh token",
    "details": {},
    "timestamp": 1773244866867
}
```

---

### Logout

Clear the current session. Requires authentication.

```
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Logged out successfully",
    "timestamp": 1773245463193
}
```

---

## User Management

### Get Profile

Get the current authenticated user's profile.

```
GET /api/v1/user/me
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Profile retrieved successfully",
    "data": {
        "id": "69b1916f12c7fe019f8d1485",
        "username": "sonu",
        "email": "sonu@legit.dev",
        "fullName": "Sonu Kumar",
        "phoneNumber": "+919876543210",
        "isVerified": false,
        "role": "USER",
        "createdAt": 1773244783147
    },
    "timestamp": 1773244813615
}
```

---

### Update Profile

Update name and/or phone number. Only send the fields you want to update.

```
PUT /api/v1/user/me
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body:**
```json
{
    "fullName": "Sonu Kumar Singh",
    "phoneNumber": "+919988776655"
}
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Profile updated successfully",
    "data": {
        "id": "69b1916f12c7fe019f8d1485",
        "username": "sonu",
        "email": "sonu@legit.dev",
        "fullName": "Sonu Kumar Singh",
        "phoneNumber": "+919988776655",
        "isVerified": false,
        "role": "USER",
        "createdAt": 1773244783147
    },
    "timestamp": 1773244813817
}
```

---

### Change Password

Change the current user's password. Clears the session on success.

```
POST /api/v1/user/change-password
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body:**
```json
{
    "currentPassword": "Legit@2026!",
    "newPassword": "NewSecure@2026!"
}
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Password changed successfully. Please log in again.",
    "timestamp": 1773244900000
}
```

**Error Response — Wrong Current Password (401):**
```json
{
    "code": "AUTH_001",
    "message": "Current password is incorrect",
    "details": {},
    "timestamp": 1773244900000
}
```

---

### Get Session

Get current session information.

```
GET /api/v1/user/session
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Session active",
    "data": {
        "userId": "69b1916f12c7fe019f8d1485",
        "username": "sonu",
        "role": "USER",
        "sessionId": "aB3xY9...",
        "createdAt": 1773244783000
    },
    "timestamp": 1773244813961
}
```

**Error Response — No Session (401):**
```json
{
    "code": "AUTH_005",
    "message": "No active session found. Please log in.",
    "details": {},
    "timestamp": 1773244813961
}
```

---

## Document Vault

### Upload Document

Upload a new document to the user's encrypted vault.

```
POST /api/v1/documents
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body (Aadhaar Card):**
```json
{
    "documentType": "AADHAAR_CARD",
    "documentNumber": "123456789012",
    "documentName": "My Aadhaar Card",
    "metadata": {
        "fullName": "Sonu Kumar",
        "dateOfBirth": "1998-05-15",
        "address": "123 Main Street, Bangalore, Karnataka 560001",
        "gender": "Male",
        "fatherName": "Rajesh Kumar",
        "extraFields": {}
    },
    "rawData": "{\"aadhaar\":\"123456789012\",\"name\":\"Sonu Kumar\",\"dob\":\"15-05-1998\"}",
    "issuedBy": "UIDAI",
    "issuedAt": null,
    "expiresAt": null
}
```

**Request Body (PAN Card):**
```json
{
    "documentType": "PAN_CARD",
    "documentNumber": "ABCDE1234F",
    "documentName": "My PAN Card",
    "metadata": {
        "fullName": "Sonu Kumar",
        "dateOfBirth": "1998-05-15",
        "fatherName": "Rajesh Kumar"
    },
    "rawData": "{\"pan\":\"ABCDE1234F\",\"name\":\"Sonu Kumar\"}",
    "issuedBy": "Income Tax Department"
}
```

**Request Body (Passport):**
```json
{
    "documentType": "PASSPORT",
    "documentNumber": "A1234567",
    "documentName": "My Passport",
    "metadata": {
        "fullName": "Sonu Kumar",
        "dateOfBirth": "1998-05-15",
        "address": "123 Main Street, Bangalore",
        "gender": "Male",
        "fatherName": "Rajesh Kumar"
    },
    "rawData": "{\"passport\":\"A1234567\",\"name\":\"Sonu Kumar\"}",
    "issuedBy": "Ministry of External Affairs",
    "issuedAt": 1672531200000,
    "expiresAt": 1988150400000
}
```

**Request Body (Driving License):**
```json
{
    "documentType": "DRIVING_LICENSE",
    "documentNumber": "KA0120190001234",
    "documentName": "My Driving License",
    "metadata": {
        "fullName": "Sonu Kumar",
        "dateOfBirth": "1998-05-15",
        "address": "123 Main Street, Bangalore"
    },
    "rawData": "{\"dl\":\"KA0120190001234\",\"name\":\"Sonu Kumar\"}",
    "issuedBy": "RTO Bangalore",
    "expiresAt": 1988150400000
}
```

**Success Response (201):**
```json
{
    "success": true,
    "message": "Document uploaded successfully to your vault",
    "data": {
        "id": "69b1938b10ba5424c008a1dc",
        "userId": "69b1936810ba5424c008a1da",
        "documentType": "AADHAAR_CARD",
        "documentNumber": "XXXX-XXXX-9012",
        "documentName": "My Aadhaar Card",
        "metadata": {
            "fullName": "Sonu Kumar",
            "dateOfBirth": "1998-05-15",
            "address": "123 Main Street, Bangalore, Karnataka 560001",
            "fatherName": "Rajesh Kumar",
            "gender": "Male",
            "extraFields": {}
        },
        "status": "PENDING",
        "issuedBy": "UIDAI",
        "issuedAt": null,
        "expiresAt": null,
        "createdAt": 1773245323146,
        "updatedAt": 1773245323146
    },
    "timestamp": 1773245323183
}
```

> ⚠️ Note: `documentNumber` is always **masked** in responses (Aadhaar: `XXXX-XXXX-9012`, PAN: `ABXXXXX34F`)

**Error Response — Duplicate (409):**
```json
{
    "code": "DOC_002",
    "message": "A AADHAAR_CARD with this number is already stored in your vault",
    "details": {},
    "timestamp": 1773245445138
}
```

**Error Response — Validation Failed (422):**
```json
{
    "code": "GEN_002",
    "message": "Validation failed",
    "details": {
        "documentNumber": "Aadhaar number must be 12 digits"
    },
    "timestamp": 1773245445275
}
```

---

### List All Documents

List all documents in the current user's vault.

```
GET /api/v1/documents
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Documents retrieved successfully",
    "data": {
        "documents": [
            {
                "id": "69b1938b10ba5424c008a1dc",
                "documentType": "AADHAAR_CARD",
                "documentName": "My Aadhaar Card",
                "documentNumberMasked": "XXXX-XXXX-9012",
                "status": "PENDING",
                "createdAt": 1773245323146
            },
            {
                "id": "69b1938b10ba5424c008a1dd",
                "documentType": "PAN_CARD",
                "documentName": "My PAN Card",
                "documentNumberMasked": "ABXXXXX34F",
                "status": "PENDING",
                "createdAt": 1773245323988
            }
        ],
        "total": 2
    },
    "timestamp": 1773245342116
}
```

---

### Get Document by ID

Get details of a specific document (masked, no raw data exposed).

```
GET /api/v1/documents/{documentId}
Authorization: Bearer <token>
```

**Example:**
```
GET /api/v1/documents/69b1938b10ba5424c008a1dc
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Document retrieved successfully",
    "data": {
        "id": "69b1938b10ba5424c008a1dc",
        "userId": "69b1936810ba5424c008a1da",
        "documentType": "AADHAAR_CARD",
        "documentNumber": "XXXX-XXXX-9012",
        "documentName": "My Aadhaar Card",
        "metadata": {
            "fullName": "Sonu Kumar",
            "dateOfBirth": "1998-05-15",
            "address": "123 Main Street, Bangalore, Karnataka 560001",
            "fatherName": "Rajesh Kumar",
            "gender": "Male",
            "extraFields": {}
        },
        "status": "PENDING",
        "issuedBy": "UIDAI",
        "issuedAt": null,
        "expiresAt": null,
        "createdAt": 1773245323146,
        "updatedAt": 1773245323146
    },
    "timestamp": 1773245342252
}
```

**Error Response — Not Found (404):**
```json
{
    "code": "DOC_001",
    "message": "Document not found",
    "details": {},
    "timestamp": 1773245342252
}
```

**Error Response — Not Your Document (403):**
```json
{
    "code": "AUTH_004",
    "message": "You do not have access to this document",
    "details": {},
    "timestamp": 1773245445639
}
```

---

### Filter Documents by Type

List documents filtered by document type.

```
GET /api/v1/documents/type/{documentType}
Authorization: Bearer <token>
```

**Example:**
```
GET /api/v1/documents/type/AADHAAR_CARD
GET /api/v1/documents/type/PAN_CARD
GET /api/v1/documents/type/PASSPORT
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Documents of type AADHAAR_CARD retrieved successfully",
    "data": {
        "documents": [
            {
                "id": "69b1938b10ba5424c008a1dc",
                "documentType": "AADHAAR_CARD",
                "documentName": "My Aadhaar Card",
                "documentNumberMasked": "XXXX-XXXX-9012",
                "status": "PENDING",
                "createdAt": 1773245323146
            }
        ],
        "total": 1
    },
    "timestamp": 1773245342447
}
```

---

### Update Document Status

Update the status of a document you own.

```
PATCH /api/v1/documents/{documentId}/status
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body:**
```json
{
    "status": "VERIFIED"
}
```

**Valid statuses:** `PENDING`, `VERIFIED`, `REJECTED`, `EXPIRED`

**Success Response (200):**
```json
{
    "success": true,
    "message": "Document status updated to VERIFIED",
    "data": {
        "id": "69b1938b10ba5424c008a1dc",
        "userId": "69b1936810ba5424c008a1da",
        "documentType": "AADHAAR_CARD",
        "documentNumber": "XXXX-XXXX-9012",
        "documentName": "My Aadhaar Card",
        "metadata": { "..." : "..." },
        "status": "VERIFIED",
        "issuedBy": "UIDAI",
        "createdAt": 1773245323146,
        "updatedAt": 1773245400000
    },
    "timestamp": 1773245400000
}
```

---

### Delete Document

Delete a document from your vault.

```
DELETE /api/v1/documents/{documentId}
Authorization: Bearer <token>
```

**Example:**
```
DELETE /api/v1/documents/69b1938b10ba5424c008a1dd
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Document deleted successfully from your vault",
    "timestamp": 1773245466655
}
```

---

### Verify Document Integrity

Check if a stored document's encrypted data has been tampered with (SHA-256 hash check).

```
GET /api/v1/documents/{documentId}/verify-integrity
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Integrity check passed",
    "data": {
        "documentId": "69b1938b10ba5424c008a1dc",
        "integrityIntact": true,
        "message": "Document integrity verified — data has not been tampered with",
        "checkedAt": 1773245342835
    },
    "timestamp": 1773245342835
}
```

**Tampered Response (200 but `integrityIntact: false`):**
```json
{
    "success": true,
    "message": "Integrity check failed",
    "data": {
        "documentId": "69b1938b10ba5424c008a1dc",
        "integrityIntact": false,
        "message": "WARNING: Document integrity check failed — data may have been tampered with",
        "checkedAt": 1773245342835
    },
    "timestamp": 1773245342835
}
```

---

### Supported Document Types

List all supported document types with descriptions.

```
GET /api/v1/documents/types/supported
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Supported document types",
    "data": [
        {
            "type": "AADHAAR_CARD",
            "name": "Aadhaar card",
            "description": "12-digit unique identity number issued by UIDAI"
        },
        {
            "type": "PAN_CARD",
            "name": "Pan card",
            "description": "Permanent Account Number issued by the Income Tax Department"
        },
        {
            "type": "PASSPORT",
            "name": "Passport",
            "description": "Travel document issued by the Government of India"
        },
        {
            "type": "DRIVING_LICENSE",
            "name": "Driving license",
            "description": "License to drive motor vehicles issued by RTO"
        },
        {
            "type": "VOTER_ID",
            "name": "Voter id",
            "description": "Electoral photo identity card issued by the Election Commission"
        },
        {
            "type": "BANK_STATEMENT",
            "name": "Bank statement",
            "description": "Official bank account statement"
        },
        {
            "type": "ADDRESS_PROOF",
            "name": "Address proof",
            "description": "Any valid address proof document"
        },
        {
            "type": "INCOME_PROOF",
            "name": "Income proof",
            "description": "Salary slips, ITR, or other income proof documents"
        },
        {
            "type": "EDUCATION_CERTIFICATE",
            "name": "Education certificate",
            "description": "Degree, diploma, or other educational certificates"
        },
        {
            "type": "OTHER",
            "name": "Other",
            "description": "Any other document type not listed above"
        }
    ],
    "timestamp": 1773245342890
}
```

---

## Verification Pipeline — User

These endpoints are for **end users** who own documents and manage verification requests.

### List All Contracts

List all verification contracts targeting the current user.

```
GET /api/v1/pipeline/user/contracts
Authorization: Bearer <token>
```

**Optional query parameter:** `?status=PENDING_APPROVAL` (filter by status)

**Success Response (200):**
```json
{
    "success": true,
    "message": "Your verification contracts retrieved successfully",
    "data": {
        "contracts": [
            {
                "contractId": "69b193b510ba5424c008a1de",
                "requesterName": "examplebank",
                "purpose": "KYC verification for opening a savings account at ExampleBank",
                "requiredDocumentTypes": ["AADHAAR_CARD", "PAN_CARD"],
                "requiredFields": ["FULL_NAME", "DATE_OF_BIRTH", "ADDRESS", "IDENTITY_PROOF", "AGE_VERIFICATION"],
                "status": "VERIFIED",
                "createdAt": 1773245365940,
                "expiresAt": 1773331765939
            }
        ],
        "total": 1
    },
    "timestamp": 1773245467000
}
```

---

### List Pending Contracts

List only contracts that need the user's approval.

```
GET /api/v1/pipeline/user/contracts/pending
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "You have 1 pending verification request(s)",
    "data": {
        "contracts": [
            {
                "contractId": "69b193b510ba5424c008a1de",
                "requesterName": "examplebank",
                "purpose": "KYC verification for opening a savings account at ExampleBank",
                "requiredDocumentTypes": ["AADHAAR_CARD", "PAN_CARD"],
                "requiredFields": ["FULL_NAME", "DATE_OF_BIRTH", "ADDRESS", "IDENTITY_PROOF", "AGE_VERIFICATION"],
                "status": "PENDING_APPROVAL",
                "createdAt": 1773245365940,
                "expiresAt": 1773331765939
            }
        ],
        "total": 1
    },
    "timestamp": 1773245378222
}
```

**Empty Response (200):**
```json
{
    "success": true,
    "message": "No pending verification requests",
    "data": {
        "contracts": [],
        "total": 0
    },
    "timestamp": 1773245378222
}
```

---

### Approve/Reject Contract

User approves or rejects a verification contract. On approval, a disposable key is generated.

```
POST /api/v1/pipeline/user/contracts/approve
Authorization: Bearer <token>
Content-Type: application/json
```

**Approve Request:**
```json
{
    "contractId": "69b193b510ba5424c008a1de",
    "approved": true
}
```

**Approve Success Response (200):**
```json
{
    "success": true,
    "message": "Contract approved. Disposable key generated for service provider.",
    "data": {
        "contractId": "69b193b510ba5424c008a1de",
        "disposableKey": "lgk_1HSy2yLymGRRGLPCs4QCue0tzCkrg1DOKPj4VwFJqfOLknXP0V8enMdKROQcHVBB",
        "expiresAt": 1773245678372,
        "message": "Disposable key generated. It expires in 300 seconds. Single-use only."
    },
    "timestamp": 1773245378414
}
```

> ⚠️ The `disposableKey` must be passed to the service provider. It expires in 5 minutes and is single-use.

**Reject Request:**
```json
{
    "contractId": "69b193b510ba5424c008a1de",
    "approved": false
}
```

**Reject Success Response (200):**
```json
{
    "success": true,
    "message": "Verification contract rejected",
    "timestamp": 1773245378414
}
```

**Error Response — Already Actioned (400):**
```json
{
    "code": "CONTRACT_002",
    "message": "This contract has already been approved",
    "details": {},
    "timestamp": 1773245378414
}
```

**Error Response — Expired (400):**
```json
{
    "code": "CONTRACT_003",
    "message": "This verification contract has expired",
    "details": {},
    "timestamp": 1773245378414
}
```

---

### View Contract Details

View details of a specific contract targeting the user.

```
GET /api/v1/pipeline/user/contracts/{contractId}
Authorization: Bearer <token>
```

**Example:**
```
GET /api/v1/pipeline/user/contracts/69b193b510ba5424c008a1de
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Contract details retrieved",
    "data": {
        "contractId": "69b193b510ba5424c008a1de",
        "requesterName": "examplebank",
        "userId": "69b1936810ba5424c008a1da",
        "purpose": "KYC verification for opening a savings account at ExampleBank",
        "status": "VERIFIED",
        "result": {
            "fieldResults": {
                "FULL_NAME": { "field": "FULL_NAME", "verified": true, "message": "Full name verified against documents" },
                "DATE_OF_BIRTH": { "field": "DATE_OF_BIRTH", "verified": true, "message": "Date of birth verified" },
                "ADDRESS": { "field": "ADDRESS", "verified": true, "message": "Address verified" },
                "IDENTITY_PROOF": { "field": "IDENTITY_PROOF", "verified": true, "message": "Identity proof verified" },
                "AGE_VERIFICATION": { "field": "AGE_VERIFICATION", "verified": true, "message": "Age verification passed (18+)" }
            },
            "overallStatus": "PASS",
            "proofHash": "2f09a9ea59034ac2...",
            "verifiedAt": 1773245395535,
            "verificationToken": "eyJhbGciOiJIUzI1NiIs..."
        },
        "createdAt": 1773245365940,
        "verifiedAt": 1773245395535
    },
    "timestamp": 1773245420528
}
```

---

### Revoke Contract

User revokes a previously approved contract before verification is executed. Burns the disposable key.

```
POST /api/v1/pipeline/user/contracts/revoke
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body:**
```json
{
    "contractId": "69b193b510ba5424c008a1de"
}
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Contract revoked successfully. Disposable key has been burned.",
    "timestamp": 1773245500000
}
```

**Error — Already Verified (400):**
```json
{
    "code": "CONTRACT_002",
    "message": "Cannot revoke a contract that has already been verified or is in progress",
    "details": {},
    "timestamp": 1773245500000
}
```

---

### View Verification History

View all completed, expired, revoked, and rejected contracts.

```
GET /api/v1/pipeline/user/contracts/history
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Verification history retrieved (1 records)",
    "data": {
        "contracts": [
            {
                "contractId": "69b193b510ba5424c008a1de",
                "requesterName": "examplebank",
                "purpose": "KYC verification for opening a savings account at ExampleBank",
                "requiredDocumentTypes": ["AADHAAR_CARD", "PAN_CARD"],
                "requiredFields": ["FULL_NAME", "DATE_OF_BIRTH", "ADDRESS", "IDENTITY_PROOF", "AGE_VERIFICATION"],
                "status": "VERIFIED",
                "createdAt": 1773245365940,
                "expiresAt": 1773331765939
            }
        ],
        "total": 1
    },
    "timestamp": 1773245420681
}
```

---

## Verification Pipeline — Service Provider

These endpoints require **SERVICE_PROVIDER** or **ADMIN** role.

### Create Contract

Service provider creates a verification contract for a user.

```
POST /api/v1/pipeline/contracts
Authorization: Bearer <service_provider_token>
Content-Type: application/json
```

**Request Body:**
```json
{
    "userId": "69b1936810ba5424c008a1da",
    "requiredDocumentTypes": ["AADHAAR_CARD", "PAN_CARD"],
    "requiredFields": ["FULL_NAME", "DATE_OF_BIRTH", "ADDRESS", "IDENTITY_PROOF", "AGE_VERIFICATION"],
    "purpose": "KYC verification for opening a savings account at ExampleBank"
}
```

**Fields:**
- `userId`: The target user's ID (the person to verify)
- `requiredDocumentTypes`: Array of document types needed (see [Enums](#enums--constants))
- `requiredFields`: Array of fields to verify (see [Enums](#enums--constants))
- `purpose`: Must be at least 10 chars — explain WHY you need this verification

**Success Response (201):**
```json
{
    "success": true,
    "message": "Verification contract created. Waiting for user approval.",
    "data": {
        "contractId": "69b193b510ba5424c008a1de",
        "requesterName": "examplebank",
        "purpose": "KYC verification for opening a savings account at ExampleBank",
        "requiredDocumentTypes": ["AADHAAR_CARD", "PAN_CARD"],
        "requiredFields": ["FULL_NAME", "DATE_OF_BIRTH", "ADDRESS", "IDENTITY_PROOF", "AGE_VERIFICATION"],
        "status": "PENDING_APPROVAL",
        "createdAt": 1773245365940,
        "expiresAt": 1773331765939
    },
    "timestamp": 1773245365993
}
```

**Error — User Missing Documents (400):**
```json
{
    "code": "CONTRACT_008",
    "message": "User is missing required documents: PASSPORT",
    "details": {},
    "timestamp": 1773245365993
}
```

**Error — Forbidden / Wrong Role (403):**
```json
{
    "code": "AUTH_004",
    "message": "Access denied. Service provider or admin role required.",
    "details": {},
    "timestamp": 1773245365993
}
```

---

### Execute Verification

Service provider uses the disposable key to execute verification. The key is burned immediately.

```
POST /api/v1/pipeline/verify
Authorization: Bearer <service_provider_token>
Content-Type: application/json
```

**Request Body:**
```json
{
    "contractId": "69b193b510ba5424c008a1de",
    "disposableKey": "lgk_1HSy2yLymGRRGLPCs4QCue0tzCkrg1DOKPj4VwFJqfOLknXP0V8enMdKROQcHVBB"
}
```

**Success Response — All Fields Passed (200):**
```json
{
    "success": true,
    "message": "Verification completed. Status: VERIFIED",
    "data": {
        "contractId": "69b193b510ba5424c008a1de",
        "requesterName": "examplebank",
        "userId": "69b1936810ba5424c008a1da",
        "purpose": "KYC verification for opening a savings account at ExampleBank",
        "status": "VERIFIED",
        "result": {
            "fieldResults": {
                "FULL_NAME": {
                    "field": "FULL_NAME",
                    "verified": true,
                    "message": "Full name verified against documents"
                },
                "DATE_OF_BIRTH": {
                    "field": "DATE_OF_BIRTH",
                    "verified": true,
                    "message": "Date of birth verified"
                },
                "ADDRESS": {
                    "field": "ADDRESS",
                    "verified": true,
                    "message": "Address verified"
                },
                "IDENTITY_PROOF": {
                    "field": "IDENTITY_PROOF",
                    "verified": true,
                    "message": "Identity proof verified"
                },
                "AGE_VERIFICATION": {
                    "field": "AGE_VERIFICATION",
                    "verified": true,
                    "message": "Age verification passed (18+)"
                }
            },
            "overallStatus": "PASS",
            "proofHash": "2f09a9ea59034ac23f87091d4a06907fdb57e84c56284ad68dd81ac1cd0e489f",
            "verifiedAt": 1773245395535,
            "verificationToken": "eyJhbGciOiJIUzI1NiIs..."
        },
        "createdAt": 1773245365940,
        "verifiedAt": 1773245395535
    },
    "timestamp": 1773245395564
}
```

> The `verificationToken` is a signed JWT valid for 1 year — store it as proof of verification.

**Error — Invalid Key (401):**
```json
{
    "code": "CONTRACT_004",
    "message": "Invalid disposable key",
    "details": {},
    "timestamp": 1773245420398
}
```

**Error — Key Expired (400):**
```json
{
    "code": "CONTRACT_005",
    "message": "Disposable key has expired. Request a new verification contract.",
    "details": {},
    "timestamp": 1773245420398
}
```

**Error — Already Verified / Replay Attack (400):**
```json
{
    "code": "CONTRACT_009",
    "message": "Contract must be approved before verification can be executed. Current status: VERIFIED",
    "details": {},
    "timestamp": 1773245420398
}
```

---

### List Requester Contracts

Service provider lists all contracts they've created.

```
GET /api/v1/pipeline/contracts/requester
Authorization: Bearer <service_provider_token>
```

**Optional query parameter:** `?status=VERIFIED`

**Success Response (200):**
```json
{
    "success": true,
    "message": "Contracts retrieved successfully",
    "data": {
        "contracts": [
            {
                "contractId": "69b193b510ba5424c008a1de",
                "requesterName": "examplebank",
                "purpose": "KYC verification for opening a savings account at ExampleBank",
                "requiredDocumentTypes": ["AADHAAR_CARD", "PAN_CARD"],
                "requiredFields": ["FULL_NAME", "DATE_OF_BIRTH", "ADDRESS", "IDENTITY_PROOF", "AGE_VERIFICATION"],
                "status": "VERIFIED",
                "createdAt": 1773245365940,
                "expiresAt": 1773331765939
            }
        ],
        "total": 1
    },
    "timestamp": 1773245420836
}
```

---

### Get Verification Result

Get the verification result for a specific contract. Available to both the service provider and the user.

```
GET /api/v1/pipeline/contracts/{contractId}/result
Authorization: Bearer <token>
```

**Example:**
```
GET /api/v1/pipeline/contracts/69b193b510ba5424c008a1de/result
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Verification result retrieved",
    "data": {
        "contractId": "69b193b510ba5424c008a1de",
        "requesterName": "examplebank",
        "userId": "69b1936810ba5424c008a1da",
        "purpose": "KYC verification for opening a savings account at ExampleBank",
        "status": "VERIFIED",
        "result": {
            "fieldResults": {
                "FULL_NAME": { "field": "FULL_NAME", "verified": true, "message": "Full name verified against documents" },
                "DATE_OF_BIRTH": { "field": "DATE_OF_BIRTH", "verified": true, "message": "Date of birth verified" },
                "ADDRESS": { "field": "ADDRESS", "verified": true, "message": "Address verified" },
                "IDENTITY_PROOF": { "field": "IDENTITY_PROOF", "verified": true, "message": "Identity proof verified" },
                "AGE_VERIFICATION": { "field": "AGE_VERIFICATION", "verified": true, "message": "Age verification passed (18+)" }
            },
            "overallStatus": "PASS",
            "proofHash": "2f09a9ea5903...",
            "verifiedAt": 1773245395535,
            "verificationToken": "eyJhbGciOiJIUzI1NiIs..."
        },
        "createdAt": 1773245365940,
        "verifiedAt": 1773245395535
    },
    "timestamp": 1773245420528
}
```

---

## Pipeline Admin

These endpoints require **ADMIN** role.

### Cleanup Expired Contracts

Manually trigger cleanup of expired contracts and burned disposable keys.

```
POST /api/v1/pipeline/admin/cleanup
Authorization: Bearer <admin_token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Cleanup completed. 3 expired contracts processed.",
    "data": {
        "expiredContractsCleaned": 3,
        "cleanedAt": 1773245500000
    },
    "timestamp": 1773245500000
}
```

---

### Admin View User Contracts

View all contracts for a specific user.

```
GET /api/v1/pipeline/admin/contracts/user/{userId}
Authorization: Bearer <admin_token>
```

**Example:**
```
GET /api/v1/pipeline/admin/contracts/user/69b1936810ba5424c008a1da
```

---

### Admin View Requester Contracts

View all contracts from a specific service provider.

```
GET /api/v1/pipeline/admin/contracts/requester/{requesterId}
Authorization: Bearer <admin_token>
```

**Example:**
```
GET /api/v1/pipeline/admin/contracts/requester/69b1936910ba5424c008a1db
```

---

### Admin View Contract Details

View full details of any contract.

```
GET /api/v1/pipeline/admin/contracts/{contractId}
Authorization: Bearer <admin_token>
```

---

## Document Admin

These endpoints require **ADMIN** role.

### Admin Update Document Status

Update any document's status (e.g., mark as verified after manual review).

```
PATCH /api/v1/admin/documents/{documentId}/status
Authorization: Bearer <admin_token>
Content-Type: application/json
```

**Request Body:**
```json
{
    "status": "VERIFIED"
}
```

---

### Admin View User Documents

View documents for any user.

```
GET /api/v1/admin/documents/user/{userId}
Authorization: Bearer <admin_token>
```

**Example:**
```
GET /api/v1/admin/documents/user/69b1936810ba5424c008a1da
```

---

## Pipeline Info (Public)

These endpoints are publicly accessible — no authentication required.

### How It Works

Detailed explanation of the Legit verification pipeline flow.

```
GET /api/v1/pipeline/info/how-it-works
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Legit Pipeline — How It Works",
    "data": {
        "title": "Legit Secure Verification Pipeline",
        "description": "Zero-document-sharing verification. Your documents never leave our server.",
        "steps": [
            { "step": 1, "action": "CREATE_CONTRACT", "actor": "Service Provider", "endpoint": "POST /api/v1/pipeline/contracts", "description": "..." },
            { "step": 2, "action": "USER_REVIEW", "actor": "User", "endpoint": "GET /api/v1/pipeline/user/contracts/pending", "description": "..." },
            { "step": 3, "action": "APPROVE_OR_REJECT", "actor": "User", "endpoint": "POST /api/v1/pipeline/user/contracts/approve", "description": "..." },
            { "step": 4, "action": "EXECUTE_VERIFICATION", "actor": "Service Provider", "endpoint": "POST /api/v1/pipeline/verify", "description": "..." },
            { "step": 5, "action": "RECEIVE_RESULT", "actor": "Service Provider", "endpoint": "GET /api/v1/pipeline/contracts/{id}/result", "description": "..." }
        ],
        "keyPrinciples": [
            "Documents are encrypted at rest with AES-256-GCM and NEVER leave the server",
            "Disposable keys are single-use, short-lived, and cryptographically random",
            "Service providers only receive YES/NO verification results, never actual document data",
            "Every verification generates a signed proof token that can be independently validated",
            "Users have full control — they can approve, reject, or revoke contracts at any time",
            "Complete audit trail of all verification activities"
        ]
    },
    "timestamp": 1773244726340
}
```

---

### Verification Fields

List all available verification fields that can be requested in a contract.

```
GET /api/v1/pipeline/info/verification-fields
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Available verification fields",
    "data": [
        { "field": "FULL_NAME", "name": "Full name", "description": "Verify the full name of the user from their documents" },
        { "field": "DATE_OF_BIRTH", "name": "Date of birth", "description": "Verify the date of birth of the user" },
        { "field": "ADDRESS", "name": "Address", "description": "Verify the address of the user from address-bearing documents" },
        { "field": "GENDER", "name": "Gender", "description": "Verify the gender of the user" },
        { "field": "FATHER_NAME", "name": "Father name", "description": "Verify the father's name from identity documents" },
        { "field": "DOCUMENT_VALIDITY", "name": "Document validity", "description": "Check if all required documents are valid and not expired" },
        { "field": "DOCUMENT_NUMBER_MATCH", "name": "Document number match", "description": "Verify that document numbers exist and are valid" },
        { "field": "IDENTITY_PROOF", "name": "Identity proof", "description": "Verify that the user has a valid identity proof document" },
        { "field": "ADDRESS_PROOF", "name": "Address proof", "description": "Verify that the user has a valid address proof document" },
        { "field": "AGE_VERIFICATION", "name": "Age verification", "description": "Verify that the user meets the minimum age requirement (18+)" }
    ],
    "timestamp": 1773244726602
}
```

---

### Contract Statuses

List all possible contract statuses and their meanings.

```
GET /api/v1/pipeline/info/contract-statuses
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Contract status definitions",
    "data": [
        { "status": "PENDING_APPROVAL", "name": "Pending approval", "description": "Contract created, waiting for user to approve or reject" },
        { "status": "APPROVED", "name": "Approved", "description": "User approved the contract, disposable key has been generated" },
        { "status": "REJECTED", "name": "Rejected", "description": "User rejected the verification request" },
        { "status": "VERIFICATION_IN_PROGRESS", "name": "Verification in progress", "description": "Verification is currently being processed on the server" },
        { "status": "VERIFIED", "name": "Verified", "description": "Verification completed successfully — result is available" },
        { "status": "FAILED", "name": "Failed", "description": "Verification process encountered an error" },
        { "status": "EXPIRED", "name": "Expired", "description": "Contract or disposable key expired before being used" },
        { "status": "REVOKED", "name": "Revoked", "description": "User revoked the contract after approval but before verification" }
    ],
    "timestamp": 1773244726779
}
```

---

## Gateway

### Health Check

```
GET /api/v1/gateway/health
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Health check complete",
    "data": {
        "status": "HEALTHY",
        "version": "1.0.0",
        "uptime": 330068,
        "services": {
            "mongodb": { "name": "MongoDB", "status": "UP", "message": "Connected and operational" },
            "user_service": { "name": "UserService", "status": "UP", "message": "Authentication & user management operational" },
            "document_service": { "name": "DocumentService", "status": "UP", "message": "Document vault operational" },
            "pipeline_service": { "name": "DataPipelineService", "status": "UP", "message": "Verification pipeline operational" }
        },
        "timestamp": 1773244718864
    },
    "timestamp": 1773244718864
}
```

---

### API Info

```
GET /api/v1/gateway/info
```

Returns full API description with all endpoint groups.

---

### Endpoints List

```
GET /api/v1/gateway/endpoints
```

Returns complete directory of all API endpoints grouped by service, with methods, paths, descriptions, auth requirements, and roles.

---

### Uptime

```
GET /api/v1/gateway/uptime
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Server uptime",
    "data": {
        "uptimeMs": "330529",
        "uptimeFormatted": "0h 5m 30s",
        "startedAt": "1773244388796"
    },
    "timestamp": 1773244719341
}
```

---

### Ping

Liveness probe for health checks.

```
GET /api/v1/gateway/ping
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "pong",
    "timestamp": 1773244704807
}
```

---

## Enums & Constants

### Document Types

Use these exact strings in API requests:

| Value | Description | Validation |
|-------|-------------|------------|
| `AADHAAR_CARD` | UIDAI Aadhaar | 12 digits |
| `PAN_CARD` | Income Tax PAN | Format: `ABCDE1234F` |
| `PASSPORT` | Indian Passport | Format: `A1234567` |
| `DRIVING_LICENSE` | RTO License | Min 5 chars |
| `VOTER_ID` | Election Commission ID | Min 3 chars |
| `BANK_STATEMENT` | Bank Statement | Min 3 chars |
| `ADDRESS_PROOF` | Address Proof | Min 3 chars |
| `INCOME_PROOF` | Salary/ITR | Min 3 chars |
| `EDUCATION_CERTIFICATE` | Degree/Diploma | Min 3 chars |
| `OTHER` | Any other | Min 3 chars |

### Document Statuses

| Value | Meaning |
|-------|---------|
| `PENDING` | Uploaded, not yet verified |
| `VERIFIED` | Verified as authentic |
| `REJECTED` | Rejected as invalid |
| `EXPIRED` | Document has expired |

### User Roles

| Value | Permissions |
|-------|-------------|
| `USER` | Upload docs, approve/reject contracts, view history |
| `SERVICE_PROVIDER` | Create contracts, execute verifications, view results |
| `ADMIN` | Everything + manage users, documents, cleanup |

### Verification Fields

| Value | What It Checks |
|-------|---------------|
| `FULL_NAME` | Name exists in document metadata |
| `DATE_OF_BIRTH` | DOB exists in document metadata |
| `ADDRESS` | Address exists in address-bearing documents |
| `GENDER` | Gender field exists |
| `FATHER_NAME` | Father's name exists |
| `DOCUMENT_VALIDITY` | Documents not expired + integrity intact |
| `DOCUMENT_NUMBER_MATCH` | Document numbers exist and valid |
| `IDENTITY_PROOF` | Valid identity document present |
| `ADDRESS_PROOF` | Valid address proof present |
| `AGE_VERIFICATION` | User is 18+ based on DOB |

### Contract Statuses

| Value | Meaning |
|-------|---------|
| `PENDING_APPROVAL` | Waiting for user to approve/reject |
| `APPROVED` | User approved, disposable key generated |
| `REJECTED` | User rejected the request |
| `VERIFICATION_IN_PROGRESS` | Verification running on server |
| `VERIFIED` | Verification done, result available |
| `FAILED` | Verification encountered an error |
| `EXPIRED` | Contract or key expired |
| `REVOKED` | User revoked after approval |

### Overall Verification Status

| Value | Meaning |
|-------|---------|
| `PASS` | All requested fields verified successfully |
| `FAIL` | No fields could be verified |
| `PARTIAL` | Some fields passed, some failed |

---

## Error Codes

### Authentication Errors

| Code | HTTP | Meaning |
|------|------|---------|
| `AUTH_001` | 401 | Invalid credentials (wrong email/password) |
| `AUTH_002` | 401 | Token expired |
| `AUTH_003` | 401 | Authentication required |
| `AUTH_004` | 403 | Forbidden — insufficient permissions |
| `AUTH_005` | 401 | Session expired |

### User Errors

| Code | HTTP | Meaning |
|------|------|---------|
| `USER_001` | 404 | User not found |
| `USER_002` | 409 | Username already taken |
| `USER_003` | 400 | Invalid user data |
| `USER_004` | 409 | Email already taken |

### Document Errors

| Code | HTTP | Meaning |
|------|------|---------|
| `DOC_001` | 404 | Document not found |
| `DOC_002` | 409 | Document already exists (duplicate) |
| `DOC_003` | 400 | Invalid document data |
| `DOC_004` | 500 | Document upload failed |
| `DOC_005` | 400 | Document type not supported |

### Contract / Pipeline Errors

| Code | HTTP | Meaning |
|------|------|---------|
| `CONTRACT_001` | 404 | Contract not found |
| `CONTRACT_002` | 400 | Contract already actioned |
| `CONTRACT_003` | 400 | Contract expired |
| `CONTRACT_004` | 401 | Invalid disposable key |
| `CONTRACT_005` | 400 | Disposable key expired |
| `CONTRACT_006` | 400 | Key already used |
| `CONTRACT_007` | 500 | Verification failed |
| `CONTRACT_008` | 400 | User missing required documents |
| `CONTRACT_009` | 400 | Contract not in approved state |

### General Errors

| Code | HTTP | Meaning |
|------|------|---------|
| `GEN_001` | 500 | Internal server error |
| `GEN_002` | 422 | Validation error (check `details`) |
| `GEN_003` | 400 | Bad request |
| `GEN_004` | 429 | Rate limited |
| `GEN_005` | 503 | Service unavailable |

---

## Standard Response Formats

### Success Response

```json
{
    "success": true,
    "message": "Human-readable success message",
    "data": { },
    "timestamp": 1773244783500
}
```

### Error Response

```json
{
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
        "field1": "Field-specific error message",
        "field2": "Another field error"
    },
    "timestamp": 1773244783500
}
```

---

## Android Integration Notes

### Token Storage

- Store `token` (access token) and `refreshToken` in Android `EncryptedSharedPreferences`
- Access token expires in **1 hour** (`expiresIn: 3600`)
- Refresh token expires in **7 days**
- Use an OkHttp `Interceptor` to auto-refresh when you get a 401

### Retrofit Interface Example

```kotlin
interface LegitApi {

    // Auth
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthData>>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthData>>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<ApiResponse<AuthData>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    // User
    @GET("api/v1/user/me")
    suspend fun getProfile(): Response<ApiResponse<UserProfile>>

    @PUT("api/v1/user/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiResponse<UserProfile>>

    @POST("api/v1/user/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<Unit>>

    // Documents
    @POST("api/v1/documents")
    suspend fun uploadDocument(@Body request: DocumentUploadRequest): Response<ApiResponse<DocumentResponse>>

    @GET("api/v1/documents")
    suspend fun listDocuments(): Response<ApiResponse<DocumentListResponse>>

    @GET("api/v1/documents/{id}")
    suspend fun getDocument(@Path("id") documentId: String): Response<ApiResponse<DocumentResponse>>

    @GET("api/v1/documents/type/{type}")
    suspend fun getDocumentsByType(@Path("type") type: String): Response<ApiResponse<DocumentListResponse>>

    @HTTP(method = "PATCH", path = "api/v1/documents/{id}/status", hasBody = true)
    suspend fun updateDocumentStatus(@Path("id") documentId: String, @Body request: UpdateStatusRequest): Response<ApiResponse<DocumentResponse>>

    @DELETE("api/v1/documents/{id}")
    suspend fun deleteDocument(@Path("id") documentId: String): Response<ApiResponse<Unit>>

    @GET("api/v1/documents/{id}/verify-integrity")
    suspend fun verifyIntegrity(@Path("id") documentId: String): Response<ApiResponse<IntegrityResponse>>

    @GET("api/v1/documents/types/supported")
    suspend fun getSupportedTypes(): Response<ApiResponse<List<SupportedType>>>

    // Pipeline — User
    @GET("api/v1/pipeline/user/contracts")
    suspend fun listContracts(@Query("status") status: String? = null): Response<ApiResponse<ContractListResponse>>

    @GET("api/v1/pipeline/user/contracts/pending")
    suspend fun listPendingContracts(): Response<ApiResponse<ContractListResponse>>

    @POST("api/v1/pipeline/user/contracts/approve")
    suspend fun approveContract(@Body request: ApproveRequest): Response<ApiResponse<DisposableKeyResponse>>

    @GET("api/v1/pipeline/user/contracts/{id}")
    suspend fun getContractDetails(@Path("id") contractId: String): Response<ApiResponse<VerificationResponse>>

    @POST("api/v1/pipeline/user/contracts/revoke")
    suspend fun revokeContract(@Body request: RevokeRequest): Response<ApiResponse<Unit>>

    @GET("api/v1/pipeline/user/contracts/history")
    suspend fun getVerificationHistory(): Response<ApiResponse<ContractListResponse>>

    // Pipeline — Service Provider
    @POST("api/v1/pipeline/contracts")
    suspend fun createContract(@Body request: CreateContractRequest): Response<ApiResponse<ContractSummary>>

    @POST("api/v1/pipeline/verify")
    suspend fun executeVerification(@Body request: ExecuteVerificationRequest): Response<ApiResponse<VerificationResponse>>

    @GET("api/v1/pipeline/contracts/requester")
    suspend fun listRequesterContracts(@Query("status") status: String? = null): Response<ApiResponse<ContractListResponse>>

    @GET("api/v1/pipeline/contracts/{id}/result")
    suspend fun getVerificationResult(@Path("id") contractId: String): Response<ApiResponse<VerificationResponse>>

    // Pipeline Info (Public)
    @GET("api/v1/pipeline/info/how-it-works")
    suspend fun howItWorks(): Response<ApiResponse<PipelineFlowInfo>>

    @GET("api/v1/pipeline/info/verification-fields")
    suspend fun verificationFields(): Response<ApiResponse<List<FieldInfo>>>

    @GET("api/v1/pipeline/info/contract-statuses")
    suspend fun contractStatuses(): Response<ApiResponse<List<StatusInfo>>>

    // Gateway
    @GET("api/v1/gateway/ping")
    suspend fun ping(): Response<ApiResponse<Unit>>

    @GET("api/v1/gateway/health")
    suspend fun health(): Response<ApiResponse<HealthResponse>>
}
```

### OkHttp Auth Interceptor

```kotlin
class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = tokenManager.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
```

### OkHttp Token Refresh Authenticator

```kotlin
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: LegitApi
) : Authenticator {
    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        if (response.code == 401) {
            val refreshToken = tokenManager.getRefreshToken() ?: return null
            val refreshResponse = runBlocking {
                authApi.refreshToken(RefreshRequest(refreshToken))
            }
            if (refreshResponse.isSuccessful) {
                val newToken = refreshResponse.body()?.data?.token ?: return null
                val newRefresh = refreshResponse.body()?.data?.refreshToken ?: return null
                tokenManager.saveTokens(newToken, newRefresh)
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }
        }
        return null
    }
}
```

### Data Classes (Kotlin)

```kotlin
// Auth
data class RegisterRequest(val username: String, val email: String, val password: String, val fullName: String, val phoneNumber: String? = null)
data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class AuthData(val token: String, val refreshToken: String, val userId: String, val username: String, val role: String, val expiresIn: Long)

// User
data class UserProfile(val id: String, val username: String, val email: String, val fullName: String, val phoneNumber: String?, val isVerified: Boolean, val role: String, val createdAt: Long)
data class UpdateProfileRequest(val fullName: String? = null, val phoneNumber: String? = null)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

// Documents
data class DocumentMetadata(val fullName: String? = null, val dateOfBirth: String? = null, val address: String? = null, val fatherName: String? = null, val gender: String? = null, val extraFields: Map<String, String> = emptyMap())
data class DocumentUploadRequest(val documentType: String, val documentNumber: String, val documentName: String, val metadata: DocumentMetadata, val rawData: String, val issuedBy: String? = null, val issuedAt: Long? = null, val expiresAt: Long? = null)
data class DocumentResponse(val id: String, val userId: String, val documentType: String, val documentNumber: String, val documentName: String, val metadata: DocumentMetadata, val status: String, val issuedBy: String?, val issuedAt: Long?, val expiresAt: Long?, val createdAt: Long, val updatedAt: Long)
data class DocumentSummary(val id: String, val documentType: String, val documentName: String, val documentNumberMasked: String, val status: String, val createdAt: Long)
data class DocumentListResponse(val documents: List<DocumentSummary>, val total: Int)
data class UpdateStatusRequest(val status: String)
data class IntegrityResponse(val documentId: String, val integrityIntact: Boolean, val message: String, val checkedAt: Long)
data class SupportedType(val type: String, val name: String, val description: String)

// Pipeline
data class CreateContractRequest(val userId: String, val requiredDocumentTypes: List<String>, val requiredFields: List<String>, val purpose: String)
data class ApproveRequest(val contractId: String, val approved: Boolean)
data class RevokeRequest(val contractId: String)
data class ExecuteVerificationRequest(val contractId: String, val disposableKey: String)
data class ContractSummary(val contractId: String, val requesterName: String, val purpose: String, val requiredDocumentTypes: List<String>, val requiredFields: List<String>, val status: String, val createdAt: Long, val expiresAt: Long)
data class ContractListResponse(val contracts: List<ContractSummary>, val total: Int)
data class DisposableKeyResponse(val contractId: String, val disposableKey: String, val expiresAt: Long, val message: String)
data class FieldVerificationResult(val field: String, val verified: Boolean, val message: String?)
data class VerificationResult(val fieldResults: Map<String, FieldVerificationResult>, val overallStatus: String, val proofHash: String, val verifiedAt: Long, val verificationToken: String)
data class VerificationResponse(val contractId: String, val requesterName: String, val userId: String, val purpose: String, val status: String, val result: VerificationResult?, val createdAt: Long, val verifiedAt: Long?)

// Gateway
data class ServiceStatus(val name: String, val status: String, val message: String?)
data class HealthResponse(val status: String, val version: String, val uptime: Long, val services: Map<String, ServiceStatus>, val timestamp: Long)

// Wrapper
data class ApiResponse<T>(val success: Boolean, val message: String, val data: T?, val timestamp: Long)
data class ApiError(val code: String, val message: String, val details: Map<String, String>, val timestamp: Long)
```

### Complete Verification Flow (Android)

```kotlin
// 1. User uploads documents
val aadhaarResult = api.uploadDocument(DocumentUploadRequest(
    documentType = "AADHAAR_CARD",
    documentNumber = "123456789012",
    documentName = "My Aadhaar",
    metadata = DocumentMetadata(fullName = "Sonu Kumar", dateOfBirth = "1998-05-15", address = "Bangalore"),
    rawData = "{\"aadhaar_data\": \"...\"}",
    issuedBy = "UIDAI"
))

// 2. User checks for pending verification requests
val pending = api.listPendingContracts()
// Show in UI: "ExampleBank wants to verify your AADHAAR_CARD and PAN_CARD for KYC"

// 3. User approves
val approval = api.approveContract(ApproveRequest(
    contractId = pending.body()!!.data!!.contracts[0].contractId,
    approved = true
))
// The disposableKey in the response goes to the service provider

// 4. User can check their verification history anytime
val history = api.getVerificationHistory()
```
