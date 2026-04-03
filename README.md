# 🔐 Legit — Secure Data Verification Pipeline

> **Your documents never leave. Only verification does.**

Legit is a secure data verification platform that fundamentally rethinks how KYC and document verification works. Unlike DigiLocker which shares actual documents with service providers, Legit uses a **contractual verification pipeline** where documents are verified on-server and only YES/NO results are shared — with cryptographic proof.

---

## 🧠 The Core Idea

Traditional flow (DigiLocker, etc.):
```
Company requests documents → User shares documents → Company stores/processes documents → Data leak risk
```

**Legit's flow:**
```
Company requests VERIFICATION → User approves → Disposable key generated → 
Server verifies internally → Company gets YES/NO + proof → Key burned → Done.
```

**No document ever leaves the server. Period.**

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        API GATEWAY                               │
│              Route Aggregation · Health · Discovery               │
├──────────────┬──────────────────┬──────────────────┬─────────────┤
│              │                  │                  │             │
│  ┌───────────▼──────────┐ ┌────▼───────────┐ ┌────▼──────────┐  │
│  │    USER SERVICE       │ │  DOCUMENT      │ │  DATA         │  │
│  │                       │ │  SERVICE       │ │  PIPELINE     │  │
│  │  · JWT Auth           │ │                │ │               │  │
│  │  · Sessions           │ │  · MongoDB     │ │  · Contracts  │  │
│  │  · Registration       │ │  · AES-256-GCM │ │  · Disposable │  │
│  │  · Login/Logout       │ │  · Any Doc Type│ │    Keys       │  │
│  │  · User Management    │ │  · Integrity   │ │  · Verify     │  │
│  │  · Password Mgmt      │ │    Checks      │ │    Pipeline   │  │
│  │  · Role-Based Access  │ │  · Masking     │ │  · Proof Gen  │  │
│  └───────────┬───────────┘ └────┬───────────┘ └────┬──────────┘  │
│              │                  │                   │             │
│              └──────────────────┼───────────────────┘             │
│                                 │                                 │
│                        ┌────────▼────────┐                       │
│                        │    MongoDB       │                       │
│                        │                  │                       │
│                        │  · users         │                       │
│                        │  · documents     │                       │
│                        │  · contracts     │                       │
│                        └─────────────────┘                       │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🔑 How the Verification Pipeline Works

### Step 1: Service Provider Creates a Contract
```
POST /api/v1/pipeline/contracts
```
The service provider specifies:
- Which user they want to verify
- What document types are needed (Aadhaar, PAN, etc.)
- What fields to verify (name, DOB, address, age, etc.)
- Why they need the verification (purpose)

### Step 2: User Reviews & Approves
```
GET  /api/v1/pipeline/user/contracts/pending
POST /api/v1/pipeline/user/contracts/approve
```
The user sees exactly who is asking, what they want, and why. They approve or reject. On approval, a **disposable key** is generated — single-use, short-lived (5 minutes by default).

### Step 3: Service Provider Executes Verification
```
POST /api/v1/pipeline/verify
```
The service provider uses the disposable key to trigger verification. The key is **burned immediately** — it cannot be replayed.

### Step 4: Server-Side Verification
The pipeline runs entirely on the Legit server:
- Documents are decrypted internally
- Each required field is checked
- Document validity and integrity are verified
- A cryptographic proof hash is generated
- A signed verification token is created

### Step 5: Result Returned
```
GET /api/v1/pipeline/contracts/{id}/result
```
The service provider receives:
- ✅ **PASS** / ❌ **FAIL** / ⚠️ **PARTIAL** for each field
- Overall verification status
- Cryptographic proof hash
- Signed verification token (valid for 1 year as proof)

**What they DON'T receive:** Any actual document data. Ever.

---

## 🚀 Quick Start

### Prerequisites

- **JDK 21+** (project targets JVM 25, but 21+ works)
- **MongoDB** running on `localhost:27017`
- **Gradle** (wrapper included)

### 1. Clone & Build

```bash
cd legit
./gradlew build
```

### 2. Start MongoDB (Docker with Persistent Volume)

```bash
# Create a persistent volume so your data survives container restarts
docker volume create legit-mongo-data

# Run MongoDB with the volume mounted
docker run -d \
  --name legit-mongo \
  -p 27017:27017 \
  -v legit-mongo-data:/data/db \
  --restart unless-stopped \
  mongo:8

# Verify it's running
docker ps --filter "name=legit-mongo"
```

#### Volume Management

```bash
# Check volume info
docker volume inspect legit-mongo-data

# Stop MongoDB (data persists in the volume)
docker stop legit-mongo

# Start it back up (data is still there)
docker start legit-mongo

# Full reset — remove container AND data (irreversible)
docker rm -f legit-mongo
docker volume rm legit-mongo-data
```

> 💡 The volume `legit-mongo-data` persists all your data (users, documents, contracts) across container restarts, upgrades, and rebuilds. Only `docker volume rm` will delete it.

#### Or Just Use Docker Compose (Recommended)

```bash
# Start the full stack. MongoDB is published on localhost:27018 by default
# to avoid collisions with an existing local MongoDB or a prior legit-mongo container.
docker compose up -d

# Check status
docker compose ps

# View logs
docker compose logs -f mongodb

# Stop (data persists)
docker compose down

# Full reset — nuke everything including data
docker compose down -v
```

### 3. Run the Server

```bash
./gradlew run
```

The server starts at `http://localhost:8080`.

### 4. Verify It's Running

```bash
curl http://localhost:8080/api/v1/gateway/ping
# → {"success":true,"message":"pong"}

curl http://localhost:8080/api/v1/gateway/health
# → Full health check with service statuses

curl http://localhost:8080/api/v1/pipeline/info/how-it-works
# → Complete pipeline documentation
```

---

## 📡 API Reference

### Authentication (`/api/v1/auth`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | None | Create a new user account |
| `POST` | `/api/v1/auth/login` | None | Login with email/password, get JWT tokens |
| `POST` | `/api/v1/auth/refresh` | None | Exchange refresh token for new access token |
| `POST` | `/api/v1/auth/logout` | JWT/Session | Clear session and logout |

### User Management (`/api/v1/user`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/user/me` | JWT/Session | Get current user profile |
| `PUT` | `/api/v1/user/me` | JWT/Session | Update profile (name, phone) |
| `POST` | `/api/v1/user/change-password` | JWT/Session | Change password |
| `GET` | `/api/v1/user/session` | JWT/Session | Get session info |

### Document Vault (`/api/v1/documents`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/documents` | JWT/Session | Upload a document to your vault |
| `GET` | `/api/v1/documents` | JWT/Session | List all your documents |
| `GET` | `/api/v1/documents/{id}` | JWT/Session | Get document details (masked) |
| `GET` | `/api/v1/documents/type/{type}` | JWT/Session | Filter documents by type |
| `PATCH` | `/api/v1/documents/{id}/status` | JWT/Session | Update document status |
| `DELETE` | `/api/v1/documents/{id}` | JWT/Session | Delete a document |
| `GET` | `/api/v1/documents/{id}/verify-integrity` | JWT/Session | Verify document hasn't been tampered with |
| `GET` | `/api/v1/documents/types/supported` | JWT/Session | List supported document types |

### Verification Pipeline — Service Providers (`/api/v1/pipeline`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/pipeline/contracts` | JWT (SERVICE_PROVIDER) | Create verification contract |
| `POST` | `/api/v1/pipeline/verify` | JWT (SERVICE_PROVIDER) | Execute verification with disposable key |
| `GET` | `/api/v1/pipeline/contracts/requester` | JWT (SERVICE_PROVIDER) | List your contracts |
| `GET` | `/api/v1/pipeline/contracts/{id}/result` | JWT/Session | Get verification result |

### Verification Pipeline — Users (`/api/v1/pipeline/user`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/pipeline/user/contracts` | JWT/Session | List all your contracts |
| `GET` | `/api/v1/pipeline/user/contracts/pending` | JWT/Session | List pending approvals |
| `POST` | `/api/v1/pipeline/user/contracts/approve` | JWT/Session | Approve or reject a contract |
| `GET` | `/api/v1/pipeline/user/contracts/{id}` | JWT/Session | View contract details |
| `POST` | `/api/v1/pipeline/user/contracts/revoke` | JWT/Session | Revoke an approved contract |
| `GET` | `/api/v1/pipeline/user/contracts/history` | JWT/Session | View verification history |

### Pipeline Admin (`/api/v1/pipeline/admin`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/pipeline/admin/cleanup` | JWT (ADMIN) | Cleanup expired contracts |
| `GET` | `/api/v1/pipeline/admin/contracts/user/{id}` | JWT (ADMIN) | View user's contracts |
| `GET` | `/api/v1/pipeline/admin/contracts/requester/{id}` | JWT (ADMIN) | View requester's contracts |
| `GET` | `/api/v1/pipeline/admin/contracts/{id}` | JWT (ADMIN) | View any contract |

### Document Admin (`/api/v1/admin/documents`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `PATCH` | `/api/v1/admin/documents/{id}/status` | JWT (ADMIN) | Update any document's status |
| `GET` | `/api/v1/admin/documents/user/{userId}` | JWT (ADMIN) | View any user's documents |

### Gateway (`/api/v1/gateway`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/gateway/health` | None | Health check with service statuses |
| `GET` | `/api/v1/gateway/info` | None | API information and endpoint listing |
| `GET` | `/api/v1/gateway/endpoints` | None | Complete endpoint directory |
| `GET` | `/api/v1/gateway/uptime` | None | Server uptime |
| `GET` | `/api/v1/gateway/ping` | None | Liveness probe |

### Pipeline Info (`/api/v1/pipeline/info`) — Public

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/pipeline/info/how-it-works` | Pipeline flow explanation |
| `GET` | `/api/v1/pipeline/info/verification-fields` | Available verification fields |
| `GET` | `/api/v1/pipeline/info/contract-statuses` | Contract status definitions |

---

## 📋 Example Flows

### Register & Upload Documents

```bash
# 1. Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "sonu",
    "email": "sonu@example.com",
    "password": "MySecure@123",
    "fullName": "Sonu Kumar",
    "phoneNumber": "+919876543210"
  }'
# Response includes: token, refreshToken, userId

# 2. Upload Aadhaar
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "documentType": "AADHAAR_CARD",
    "documentNumber": "123456789012",
    "documentName": "My Aadhaar Card",
    "metadata": {
      "fullName": "Sonu Kumar",
      "dateOfBirth": "1998-05-15",
      "address": "123 Main Street, Bangalore, Karnataka 560001",
      "gender": "Male",
      "fatherName": "Rajesh Kumar"
    },
    "rawData": "{\"aadhaar_number\": \"123456789012\", \"full_data\": \"...\"}",
    "issuedBy": "UIDAI"
  }'

# 3. Upload PAN
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "documentType": "PAN_CARD",
    "documentNumber": "ABCDE1234F",
    "documentName": "My PAN Card",
    "metadata": {
      "fullName": "Sonu Kumar",
      "dateOfBirth": "1998-05-15",
      "fatherName": "Rajesh Kumar"
    },
    "rawData": "{\"pan_number\": \"ABCDE1234F\", \"full_data\": \"...\"}",
    "issuedBy": "Income Tax Department"
  }'
```

### Verification Pipeline Flow

```bash
# 1. Service provider creates a contract (needs SERVICE_PROVIDER role)
curl -X POST http://localhost:8080/api/v1/pipeline/contracts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <service-provider-token>" \
  -d '{
    "userId": "<target-user-id>",
    "requiredDocumentTypes": ["AADHAAR_CARD", "PAN_CARD"],
    "requiredFields": ["FULL_NAME", "DATE_OF_BIRTH", "IDENTITY_PROOF", "AGE_VERIFICATION"],
    "purpose": "KYC verification for opening a savings account at ExampleBank"
  }'
# Response includes: contractId

# 2. User checks pending contracts
curl http://localhost:8080/api/v1/pipeline/user/contracts/pending \
  -H "Authorization: Bearer <user-token>"
# Shows: who wants what, why, which documents

# 3. User approves the contract
curl -X POST http://localhost:8080/api/v1/pipeline/user/contracts/approve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <user-token>" \
  -d '{
    "contractId": "<contract-id>",
    "approved": true
  }'
# Response includes: disposableKey (lgk_...), expiresAt

# 4. Service provider executes verification with the disposable key
curl -X POST http://localhost:8080/api/v1/pipeline/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <service-provider-token>" \
  -d '{
    "contractId": "<contract-id>",
    "disposableKey": "lgk_<the-disposable-key>"
  }'
# Response includes:
# - fieldResults: { FULL_NAME: PASS, DATE_OF_BIRTH: PASS, ... }
# - overallStatus: PASS
# - proofHash: "abc123..."
# - verificationToken: "eyJ..." (signed JWT, valid 1 year)
# 
# What it does NOT include: any actual document data.

# 5. Check the result later
curl http://localhost:8080/api/v1/pipeline/contracts/<contract-id>/result \
  -H "Authorization: Bearer <service-provider-token>"
```

---

## 🔒 Security Features

### Document Encryption
- **AES-256-GCM** encryption at rest
- **PBKDF2WithHmacSHA256** key derivation (65,536 iterations)
- Unique salt and IV per document
- Documents are encrypted before storage and decrypted only during internal verification

### Disposable Keys
- **Cryptographically random** (48 bytes, SecureRandom)
- **Single-use** — burned immediately on use
- **Short-lived** — 5 minutes TTL by default (configurable)
- **Hash-stored** — only the SHA-256 hash is stored, never the raw key
- **Non-replayable** — key is nullified after first use attempt

### Authentication
- **JWT** access tokens (1 hour TTL)
- **JWT** refresh tokens (7 day TTL)
- **BCrypt** password hashing (cost factor 12)
- **Session cookies** as fallback auth mechanism
- **Role-based access control** (USER, ADMIN, SERVICE_PROVIDER)

### Data Integrity
- **SHA-256 hash** of original data stored alongside encrypted data
- Integrity verification endpoint to detect tampering
- Cryptographic proof hash for every verification result
- Signed verification tokens as immutable proof of past verifications

### Document Number Masking
- Aadhaar: `XXXX-XXXX-1234`
- PAN: `ABXXXXX34F`
- Others: `****1234`

---

## 📁 Project Structure

```
legit/
├── src/main/kotlin/com/sonusid/legit/
│   ├── Application.kt              # Entry point — wires everything together
│   ├── db/
│   │   └── MongoDB.kt              # MongoDB connection, collections, indexes
│   ├── models/
│   │   ├── ApiResponse.kt          # Response wrappers, error codes, exceptions
│   │   ├── Document.kt             # Document models, types, DTOs
│   │   ├── User.kt                 # User models, auth DTOs, session
│   │   └── VerificationContract.kt # Contract, disposable key, verification result
│   ├── services/
│   │   ├── UserService.kt          # Auth, JWT, sessions, user CRUD
│   │   └── DocumentService.kt      # Document vault, encryption, integrity
│   ├── pipeline/
│   │   └── DataPipelineService.kt  # THE CORE — contracts, keys, verification
│   ├── plugins/
│   │   ├── Monitoring.kt           # Call logging
│   │   ├── Security.kt             # JWT config, sessions, role-based auth
│   │   ├── Serialization.kt        # JSON content negotiation
│   │   └── StatusPages.kt          # Centralized error handling
│   ├── routes/
│   │   ├── AuthRoutes.kt           # /api/v1/auth/* and /api/v1/user/*
│   │   ├── DocumentRoutes.kt       # /api/v1/documents/*
│   │   └── PipelineRoutes.kt       # /api/v1/pipeline/*
│   └── gateway/
│       └── ApiGateway.kt           # Route aggregation, health, discovery
├── src/main/resources/
│   ├── application.yaml            # All configuration
│   └── logback.xml                 # Logging configuration
├── src/test/kotlin/
│   └── ApplicationTest.kt          # Integration tests
├── build.gradle.kts                # Dependencies
├── gradle.properties               # Versions
└── settings.gradle.kts             # Project settings
```

---

## ⚙️ Configuration

All configuration lives in `src/main/resources/application.yaml`:

| Key | Default | Description |
|-----|---------|-------------|
| `ktor.deployment.port` | `8080` | Server port |
| `jwt.secret` | (change me) | JWT signing secret |
| `jwt.issuer` | `legit-platform` | JWT issuer |
| `jwt.audience` | `legit-users` | JWT audience |
| `jwt.expirationMs` | `3600000` (1hr) | Access token TTL |
| `jwt.refreshExpirationMs` | `604800000` (7d) | Refresh token TTL |
| `mongodb.uri` | `mongodb://localhost:27017` | Native/local-run default. In Docker Compose, MongoDB is published on `mongodb://localhost:27018` unless `MONGODB_HOST_PORT` is overridden. |
| `mongodb.database` | `legit` | Database name |
| `legit.encryptionSecret` | (change me) | AES-256 encryption key |
| `legit.pipelineSecret` | (change me) | Pipeline proof secret |
| `legit.disposableKeyTtlMs` | `300000` (5min) | Disposable key TTL |

> ⚠️ **IMPORTANT:** Change all secrets before deploying to production. Use environment variables or a secret manager.

---

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Run with output
./gradlew test --info
```

---

## 📊 Supported Document Types

| Type | Validation | Description |
|------|-----------|-------------|
| `AADHAAR_CARD` | 12 digits | UIDAI unique identity number |
| `PAN_CARD` | ABCDE1234F format | Income Tax PAN |
| `PASSPORT` | A1234567 format | Travel document |
| `DRIVING_LICENSE` | 5+ chars | RTO driving license |
| `VOTER_ID` | Generic | Election Commission ID |
| `BANK_STATEMENT` | Generic | Bank account statement |
| `ADDRESS_PROOF` | Generic | Any address proof |
| `INCOME_PROOF` | Generic | Salary slips, ITR, etc. |
| `EDUCATION_CERTIFICATE` | Generic | Degrees, diplomas |
| `OTHER` | Generic | Any other document |

---

## 🔍 Verification Fields

| Field | What It Checks |
|-------|---------------|
| `FULL_NAME` | Name exists in document metadata |
| `DATE_OF_BIRTH` | DOB exists in document metadata |
| `ADDRESS` | Address exists in address-bearing documents |
| `GENDER` | Gender field exists |
| `FATHER_NAME` | Father's name exists |
| `DOCUMENT_VALIDITY` | Documents are not expired, integrity intact |
| `DOCUMENT_NUMBER_MATCH` | Document numbers exist and are valid |
| `IDENTITY_PROOF` | Valid identity document present |
| `ADDRESS_PROOF` | Valid address proof document present |
| `AGE_VERIFICATION` | User is 18+ based on DOB |

---

## 📝 Contract Lifecycle

```
PENDING_APPROVAL ──→ APPROVED ──→ VERIFICATION_IN_PROGRESS ──→ VERIFIED
        │                │                                          │
        ├──→ REJECTED    ├──→ EXPIRED (key timeout)                 │
        │                │                                          │
        ├──→ EXPIRED     ├──→ REVOKED (user revoked)               │
        │   (24hr timeout)                                          │
        │                                                      FAILED
```

---

## 🛠️ Tech Stack

- **Kotlin** 2.3.0
- **Ktor** 3.4.0 (Netty)
- **MongoDB** with Kotlin Coroutine Driver 5.3.1
- **kotlinx.serialization** for JSON
- **java-jwt** (Auth0) for JWT
- **BCrypt** (favre) for password hashing
- **AES-256-GCM** for document encryption
- **PBKDF2WithHmacSHA256** for key derivation

---

## 🤝 User Roles

| Role | Can Do |
|------|--------|
| `USER` | Upload documents, approve/reject/revoke contracts, view history |
| `SERVICE_PROVIDER` | Create verification contracts, execute verifications, view results |
| `ADMIN` | Everything above + manage users, documents, cleanup, view any contract |

---

## 💡 Why Not DigiLocker?

| Feature | DigiLocker | Legit |
|---------|-----------|-------|
| Document sharing | ✅ Shares actual documents | ❌ Never shares documents |
| Data exposure | High — documents leave the system | Zero — only YES/NO results |
| Replay attacks | Possible with shared links | Impossible — disposable keys |
| User control | Limited | Full — approve, reject, revoke |
| Audit trail | Basic | Complete with cryptographic proof |
| Data leaks | Risk at every service provider | Documents never leave server |

---

**Built with 🔥 by [@sonusid](https://github.com/sonusid)**

*Your documents never leave. Only verification does.*
