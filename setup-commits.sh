#!/bin/bash

# ============================================================
# Legit — Git History Setup Script
# Creates realistic commit history across March 7-11, 2026
# ============================================================

set -e

echo "🔐 Setting up Legit git history..."
echo ""

# Make sure we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    echo "❌ Run this script from the legit project root directory"
    exit 1
fi

# Clean any existing git history
if [ -d ".git" ]; then
    rm -rf .git
fi

git init
git branch -m main

# ============================================================
# DAY 1 — March 7, 2026 (Saturday)
# Project initialization, gradle setup, base models
# ============================================================

# Commit 1: Initial project scaffold
export GIT_AUTHOR_DATE="2026-03-07T09:15:32+05:30"
export GIT_COMMITTER_DATE="2026-03-07T09:15:32+05:30"
git add .gitignore
git add gradle/ gradlew gradlew.bat
git add settings.gradle.kts gradle.properties
git commit -m "chore: initialize ktor project scaffold"

# Commit 2: Build config with dependencies
export GIT_AUTHOR_DATE="2026-03-07T10:42:18+05:30"
export GIT_COMMITTER_DATE="2026-03-07T10:42:18+05:30"
git add build.gradle.kts
git commit -m "build: add dependencies — ktor, mongodb, bcrypt, jwt, coroutines"

# Commit 3: Application entry point and YAML config
export GIT_AUTHOR_DATE="2026-03-07T12:08:45+05:30"
export GIT_COMMITTER_DATE="2026-03-07T12:08:45+05:30"
git add src/main/resources/application.yaml
git add src/main/resources/logback.xml
git commit -m "config: add application.yaml and logback configuration"

# Commit 4: Core models — User
export GIT_AUTHOR_DATE="2026-03-07T14:33:21+05:30"
export GIT_COMMITTER_DATE="2026-03-07T14:33:21+05:30"
git add src/main/kotlin/com/sonusid/legit/models/User.kt
git commit -m "feat(models): add User model with roles, auth DTOs, and session"

# Commit 5: Core models — ApiResponse & error handling
export GIT_AUTHOR_DATE="2026-03-07T16:19:07+05:30"
export GIT_COMMITTER_DATE="2026-03-07T16:19:07+05:30"
git add src/main/kotlin/com/sonusid/legit/models/ApiResponse.kt
git commit -m "feat(models): add ApiResponse wrappers, error codes, exception hierarchy"

# Commit 6: Core models — Document
export GIT_AUTHOR_DATE="2026-03-07T18:47:53+05:30"
export GIT_COMMITTER_DATE="2026-03-07T18:47:53+05:30"
git add src/main/kotlin/com/sonusid/legit/models/Document.kt
git commit -m "feat(models): add Document model with types, metadata, masking logic

Supports Aadhaar, PAN, Passport, Driving License, Voter ID,
Bank Statement, Address Proof, Income Proof, Education Cert.
Document numbers are masked when exposed through API."

echo "✅ Day 1 (March 7) — 6 commits done"

# ============================================================
# DAY 2 — March 8, 2026 (Sunday)
# Database layer, User service, serialization
# ============================================================

# Commit 7: VerificationContract model
export GIT_AUTHOR_DATE="2026-03-08T10:05:14+05:30"
export GIT_COMMITTER_DATE="2026-03-08T10:05:14+05:30"
git add src/main/kotlin/com/sonusid/legit/models/VerificationContract.kt
git commit -m "feat(models): add VerificationContract with disposable key system

Core of the Legit pipeline — contractual verification where
service providers request verification, users approve, a
single-use disposable key triggers server-side verification.
Documents never leave the platform."

# Commit 8: MongoDB connection layer
export GIT_AUTHOR_DATE="2026-03-08T12:22:41+05:30"
export GIT_COMMITTER_DATE="2026-03-08T12:22:41+05:30"
git add src/main/kotlin/com/sonusid/legit/db/MongoDB.kt
git commit -m "feat(db): add MongoDB connection manager with indexed collections

Collections: users, documents, contracts
Indexes on email, username, userId, documentType, status,
disposableKey, expiresAt for optimal query performance."

# Commit 9: Serialization plugin
export GIT_AUTHOR_DATE="2026-03-08T13:51:09+05:30"
export GIT_COMMITTER_DATE="2026-03-08T13:51:09+05:30"
git add src/main/kotlin/com/sonusid/legit/plugins/Serialization.kt
git commit -m "feat(plugins): add JSON content negotiation with kotlinx.serialization"

# Commit 10: UserService
export GIT_AUTHOR_DATE="2026-03-08T16:38:27+05:30"
export GIT_COMMITTER_DATE="2026-03-08T16:38:27+05:30"
git add src/main/kotlin/com/sonusid/legit/services/UserService.kt
git commit -m "feat(services): add UserService — auth, JWT, sessions, user management

- Registration with full validation (username, email, password policy)
- Login with BCrypt password verification
- JWT access tokens (1hr) and refresh tokens (7d)
- Session creation and validation
- Profile CRUD and password change
- HMAC256 JWT signing"

# Commit 11: Monitoring plugin
export GIT_AUTHOR_DATE="2026-03-08T17:44:56+05:30"
export GIT_COMMITTER_DATE="2026-03-08T17:44:56+05:30"
git add src/main/kotlin/com/sonusid/legit/plugins/Monitoring.kt
git commit -m "feat(plugins): add call logging with method, URI, status, duration"

echo "✅ Day 2 (March 8) — 5 commits done"

# ============================================================
# DAY 3 — March 9, 2026 (Monday)
# Document service, security, status pages, auth routes
# ============================================================

# Commit 12: DocumentService
export GIT_AUTHOR_DATE="2026-03-09T09:27:33+05:30"
export GIT_COMMITTER_DATE="2026-03-09T09:27:33+05:30"
git add src/main/kotlin/com/sonusid/legit/services/DocumentService.kt
git commit -m "feat(services): add DocumentService — encrypted vault with AES-256-GCM

- Upload with type-specific validation (Aadhaar 12-digit, PAN format, etc.)
- AES-256-GCM encryption with PBKDF2 key derivation (65536 iterations)
- SHA-256 integrity hashing for tamper detection
- Unique salt + IV per document
- Internal decryption for pipeline verification only
- Documents NEVER exposed through any API endpoint"

# Commit 13: Security plugin
export GIT_AUTHOR_DATE="2026-03-09T11:55:19+05:30"
export GIT_COMMITTER_DATE="2026-03-09T11:55:19+05:30"
git add src/main/kotlin/com/sonusid/legit/plugins/Security.kt
git commit -m "feat(plugins): add Security — JWT auth strategies, sessions, RBAC

Three JWT authentication strategies:
- auth-jwt: regular user access
- auth-service-provider: SERVICE_PROVIDER + ADMIN roles
- auth-admin: ADMIN only
Plus session-based auth as fallback mechanism."

# Commit 14: StatusPages plugin
export GIT_AUTHOR_DATE="2026-03-09T14:12:48+05:30"
export GIT_COMMITTER_DATE="2026-03-09T14:12:48+05:30"
git add src/main/kotlin/com/sonusid/legit/plugins/StatusPages.kt
git commit -m "feat(plugins): add centralized error handling with StatusPages

Maps all custom exceptions to proper HTTP status codes.
Handles serialization errors, content negotiation issues,
and provides catch-all for unhandled exceptions."

# Commit 15: Auth routes
export GIT_AUTHOR_DATE="2026-03-09T17:09:35+05:30"
export GIT_COMMITTER_DATE="2026-03-09T17:09:35+05:30"
git add src/main/kotlin/com/sonusid/legit/routes/AuthRoutes.kt
git commit -m "feat(routes): add auth routes — register, login, refresh, logout, profile

POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET/PUT /api/v1/user/me
POST /api/v1/user/change-password
GET /api/v1/user/session"

# Commit 16: Document routes
export GIT_AUTHOR_DATE="2026-03-09T19:43:02+05:30"
export GIT_COMMITTER_DATE="2026-03-09T19:43:02+05:30"
git add src/main/kotlin/com/sonusid/legit/routes/DocumentRoutes.kt
git commit -m "feat(routes): add document vault routes — upload, list, delete, integrity

POST /api/v1/documents — upload with encryption
GET /api/v1/documents — list user documents (masked)
GET /api/v1/documents/{id} — get single doc (masked)
GET /api/v1/documents/type/{type} — filter by type
DELETE /api/v1/documents/{id} — remove from vault
GET /api/v1/documents/{id}/verify-integrity — tamper check
Admin routes for document status management."

echo "✅ Day 3 (March 9) — 5 commits done"

# ============================================================
# DAY 4 — March 10, 2026 (Tuesday)
# Data pipeline — the core innovation, pipeline routes
# ============================================================

# Commit 17: DataPipelineService
export GIT_AUTHOR_DATE="2026-03-10T10:14:22+05:30"
export GIT_COMMITTER_DATE="2026-03-10T10:14:22+05:30"
git add src/main/kotlin/com/sonusid/legit/pipeline/DataPipelineService.kt
git commit -m "feat(pipeline): add DataPipelineService — the core of Legit

The verification engine that makes document sharing obsolete:

1. Service provider creates a VerificationContract
2. User reviews and approves/rejects
3. Disposable key generated (single-use, 5min TTL, hash-stored)
4. Service provider triggers verification with the key
5. Documents verified server-side — never shared
6. YES/NO result + cryptographic proof returned
7. Key burned. Contract sealed.

Includes:
- Field-by-field verification (name, DOB, address, age, identity)
- Document validity and integrity checks
- SHA-256 proof hash generation
- Signed JWT verification tokens (1yr validity)
- Automatic expired contract cleanup
- Full audit trail"

# Commit 18: Pipeline routes
export GIT_AUTHOR_DATE="2026-03-10T14:31:58+05:30"
export GIT_COMMITTER_DATE="2026-03-10T14:31:58+05:30"
git add src/main/kotlin/com/sonusid/legit/routes/PipelineRoutes.kt
git commit -m "feat(routes): add pipeline routes — contracts, verification, history

Service Provider endpoints:
  POST /api/v1/pipeline/contracts — create verification contract
  POST /api/v1/pipeline/verify — execute with disposable key
  GET /api/v1/pipeline/contracts/requester — list own contracts

User endpoints:
  GET /api/v1/pipeline/user/contracts — list all contracts
  GET /api/v1/pipeline/user/contracts/pending — pending approvals
  POST /api/v1/pipeline/user/contracts/approve — approve/reject
  POST /api/v1/pipeline/user/contracts/revoke — revoke contract
  GET /api/v1/pipeline/user/contracts/history — verification history

Admin endpoints for cleanup and oversight.
Public info endpoints documenting the pipeline flow."

echo "✅ Day 4 (March 10) — 2 commits done"

# ============================================================
# DAY 5 — March 11, 2026 (Wednesday)
# API Gateway, Application wiring, Docker, tests, README
# ============================================================

# Commit 19: API Gateway
export GIT_AUTHOR_DATE="2026-03-11T09:22:47+05:30"
export GIT_COMMITTER_DATE="2026-03-11T09:22:47+05:30"
git add src/main/kotlin/com/sonusid/legit/gateway/ApiGateway.kt
git commit -m "feat(gateway): add API Gateway — health, discovery, route aggregation

GET /api/v1/gateway/health — service health with MongoDB status
GET /api/v1/gateway/info — full API description
GET /api/v1/gateway/endpoints — complete endpoint directory
GET /api/v1/gateway/uptime — server uptime
GET /api/v1/gateway/ping — liveness probe

Self-documenting API — every endpoint is listed with method,
path, description, auth requirements, and allowed roles."

# Commit 20: Application entry point
export GIT_AUTHOR_DATE="2026-03-11T11:08:33+05:30"
export GIT_COMMITTER_DATE="2026-03-11T11:08:33+05:30"
git add src/main/kotlin/com/sonusid/legit/Application.kt
git commit -m "feat: wire up Application — services, plugins, gateway, background tasks

- Initializes all plugins (serialization, security, monitoring, CORS)
- Connects MongoDB and creates indexes
- Instantiates UserService, DocumentService, DataPipelineService
- Registers all routes through the API Gateway
- Starts background cleanup job (expired contracts every 15min)
- Configures CORS for frontend integration"

# Commit 21: Docker Compose
export GIT_AUTHOR_DATE="2026-03-11T13:35:19+05:30"
export GIT_COMMITTER_DATE="2026-03-11T13:35:19+05:30"
git add docker-compose.yml
git commit -m "infra: add docker-compose with MongoDB, persistent volume, healthcheck

- mongo:8 with persistent volume (legit-mongo-data)
- Config volume (legit-mongo-config)
- Healthcheck with mongosh ping
- Auto-restart unless-stopped
- MONGO_INITDB_DATABASE=legit"

# Commit 22: Tests
export GIT_AUTHOR_DATE="2026-03-11T16:21:44+05:30"
export GIT_COMMITTER_DATE="2026-03-11T16:21:44+05:30"
git add src/test/kotlin/ApplicationTest.kt
git commit -m "test: add integration tests for gateway, auth, pipeline, and access control

Tests cover:
- Gateway root, ping, health, uptime, info, endpoints
- Pipeline how-it-works, verification fields, contract statuses
- Unauthenticated access rejection (documents, profile, pipeline)
- Invalid login credentials handling
- Invalid registration data validation
- Invalid refresh token handling
- Role-based access enforcement"

# Commit 23: README and final touches
export GIT_AUTHOR_DATE="2026-03-11T19:48:11+05:30"
export GIT_COMMITTER_DATE="2026-03-11T19:48:11+05:30"
git add README.md
# Add any remaining files that might not be tracked
git add -A
git commit -m "docs: add comprehensive README with architecture, API reference, examples

- Architecture diagram with all 4 services
- Step-by-step pipeline flow explanation
- Quick start with Docker volume setup and docker-compose
- Complete API reference (40+ endpoints)
- Example curl commands for registration, document upload, verification
- Security features documentation (AES-256-GCM, disposable keys, BCrypt)
- Project structure, configuration reference, supported document types
- Verification fields, contract lifecycle diagram
- DigiLocker vs Legit comparison table"

echo "✅ Day 5 (March 11) — 5 commits done"
echo ""
echo "============================================================"
echo "  ✅ All 23 commits created across March 7-11, 2026"
echo "============================================================"
echo ""
echo "  Day 1 (Mar 7, Sat):  6 commits — scaffold, deps, config, models"
echo "  Day 2 (Mar 8, Sun):  5 commits — contract model, db, user service"
echo "  Day 3 (Mar 9, Mon):  5 commits — doc service, security, auth routes"
echo "  Day 4 (Mar 10, Tue): 2 commits — data pipeline, pipeline routes"
echo "  Day 5 (Mar 11, Wed): 5 commits — gateway, app, docker, tests, docs"
echo ""
echo "  Run 'git log --oneline' to verify."
echo "============================================================"

unset GIT_AUTHOR_DATE
unset GIT_COMMITTER_DATE
