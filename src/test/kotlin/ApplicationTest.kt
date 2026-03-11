package com.sonusid.legit

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class ApplicationTest {

    @Test
    fun testGatewayRoot() = testApplication {
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "Legit")
            assertContains(body, "success")
        }
    }

    @Test
    fun testGatewayPing() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/gateway/ping").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "pong")
        }
    }

    @Test
    fun testGatewayHealth() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/gateway/health").apply {
            val body = bodyAsText()
            assertContains(body, "version")
            assertContains(body, "services")
        }
    }

    @Test
    fun testGatewayUptime() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/gateway/uptime").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "uptimeMs")
            assertContains(body, "uptimeFormatted")
        }
    }

    @Test
    fun testGatewayEndpoints() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/gateway/endpoints").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "Authentication")
            assertContains(body, "Document Vault")
            assertContains(body, "Verification Pipeline")
            assertContains(body, "API Gateway")
        }
    }

    @Test
    fun testGatewayInfo() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/gateway/info").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "Legit")
            assertContains(body, "version")
            assertContains(body, "endpoints")
        }
    }

    @Test
    fun testPipelineHowItWorks() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/pipeline/info/how-it-works").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "CREATE_CONTRACT")
            assertContains(body, "APPROVE_OR_REJECT")
            assertContains(body, "EXECUTE_VERIFICATION")
            assertContains(body, "disposable")
            assertContains(body, "keyPrinciples")
        }
    }

    @Test
    fun testPipelineVerificationFields() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/pipeline/info/verification-fields").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "FULL_NAME")
            assertContains(body, "DATE_OF_BIRTH")
            assertContains(body, "IDENTITY_PROOF")
            assertContains(body, "AGE_VERIFICATION")
        }
    }

    @Test
    fun testPipelineContractStatuses() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/pipeline/info/contract-statuses").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, "PENDING_APPROVAL")
            assertContains(body, "APPROVED")
            assertContains(body, "VERIFIED")
            assertContains(body, "EXPIRED")
            assertContains(body, "REVOKED")
        }
    }

    @Test
    fun testUnauthenticatedDocumentsAccess() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/documents").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun testUnauthenticatedUserProfileAccess() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/user/me").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun testUnauthenticatedPipelineUserContracts() = testApplication {
        application {
            module()
        }
        client.get("/api/v1/pipeline/user/contracts").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun testUnauthenticatedPipelineCreateContract() = testApplication {
        application {
            module()
        }
        client.post("/api/v1/pipeline/contracts") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.apply {
            // Should be forbidden since it requires SERVICE_PROVIDER role
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    @Test
    fun testUnauthenticatedAdminCleanup() = testApplication {
        application {
            module()
        }
        client.post("/api/v1/pipeline/admin/cleanup").apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    @Test
    fun testLoginWithInvalidCredentials() = testApplication {
        application {
            module()
        }
        client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "nonexistent@test.com", "password": "WrongPassword1!"}""")
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            val body = bodyAsText()
            assertContains(body, "Invalid email or password")
        }
    }

    @Test
    fun testRegisterWithInvalidData() = testApplication {
        application {
            module()
        }
        client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "", "email": "bad", "password": "weak", "fullName": ""}""")
        }.apply {
            assertEquals(HttpStatusCode.UnprocessableEntity, status)
        }
    }

    @Test
    fun testRefreshWithInvalidToken() = testApplication {
        application {
            module()
        }
        client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken": "invalid-token-here"}""")
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            val body = bodyAsText()
            assertContains(body, "Invalid or expired refresh token")
        }
    }

    @Test
    fun testSupportedDocumentTypes() = testApplication {
        application {
            module()
        }
        // This endpoint requires auth, so we expect 401
        client.get("/api/v1/documents/types/supported").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }
}
