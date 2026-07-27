package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.identity.IdentityToolkitClient
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentityToolkitClientTest {

    private val config = FirebaseConfig(projectId = "proj", apiKey = "test-api-key", storageBucket = "bucket")

    @Test
    fun `signInWithPassword sends the expected request shape`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedUrl: String? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method
            capturedUrl = request.url.toString()
            capturedBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"localId":"uid1","displayName":"Teacher","email":"t@example.com","idToken":"idtok","refreshToken":"reftok","expiresIn":"3600"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = IdentityToolkitClient(createHttpClient(engine), config)

        val result = client.signInWithPassword("t@example.com", "secret")

        assertEquals(HttpMethod.Post, capturedMethod)
        assertTrue(capturedUrl!!.contains("accounts:signInWithPassword"))
        assertTrue(capturedUrl!!.contains("key=test-api-key"))
        assertTrue(capturedBody!!.contains("\"email\":\"t@example.com\""))
        assertTrue(capturedBody!!.contains("\"password\":\"secret\""))
        assertIs<Resource.Success<*>>(result)
    }

    private fun errorEngine(message: String) = MockEngine { _ ->
        respond(
            content = """{"error":{"code":400,"message":"$message","errors":[]}}""",
            status = HttpStatusCode.BadRequest,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun `EMAIL_NOT_FOUND maps to Unauthorized`() = runTest {
        val client = IdentityToolkitClient(createHttpClient(errorEngine("EMAIL_NOT_FOUND")), config)
        assertEquals(Resource.Failure(RemoteError.Unauthorized), client.signInWithPassword("a@b.com", "x"))
    }

    @Test
    fun `INVALID_PASSWORD maps to Unauthorized`() = runTest {
        val client = IdentityToolkitClient(createHttpClient(errorEngine("INVALID_PASSWORD")), config)
        assertEquals(Resource.Failure(RemoteError.Unauthorized), client.signInWithPassword("a@b.com", "x"))
    }

    @Test
    fun `INVALID_LOGIN_CREDENTIALS maps to Unauthorized`() = runTest {
        val client = IdentityToolkitClient(createHttpClient(errorEngine("INVALID_LOGIN_CREDENTIALS")), config)
        assertEquals(Resource.Failure(RemoteError.Unauthorized), client.signInWithPassword("a@b.com", "x"))
    }

    @Test
    fun `USER_DISABLED maps to Forbidden`() = runTest {
        val client = IdentityToolkitClient(createHttpClient(errorEngine("USER_DISABLED")), config)
        assertEquals(Resource.Failure(RemoteError.Forbidden), client.signInWithPassword("a@b.com", "x"))
    }

    @Test
    fun `TOO_MANY_ATTEMPTS_TRY_LATER maps to retryable Server`() = runTest {
        val client = IdentityToolkitClient(createHttpClient(errorEngine("TOO_MANY_ATTEMPTS_TRY_LATER")), config)
        val result = client.signInWithPassword("a@b.com", "x")
        assertEquals(Resource.Failure(RemoteError.Server), result)
        assertTrue(RemoteError.Server.retryable)
    }

    @Test
    fun `a connection failure maps to retryable Network`() = runTest {
        val engine = MockEngine { throw IOException("connection refused") }
        val client = IdentityToolkitClient(createHttpClient(engine), config)
        assertEquals(Resource.Failure(RemoteError.Network), client.signInWithPassword("a@b.com", "x"))
    }
}
