package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.firestore.FirestoreRestClient
import com.giraffe.matn.data.remote.firestore.FirestoreValue
import com.giraffe.matn.data.remote.identity.IdentityToolkitClient
import com.giraffe.matn.data.remote.identity.TokenRefresher
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class NoOpSecretStore : SecretStore {
    override val isProtected = true
    override suspend fun put(key: String, value: String) = Resource.Success(Unit)
    override suspend fun get(key: String) = Resource.Success<String?>(null)
    override suspend fun clear(key: String) = Resource.Success(Unit)
}

class FirestoreRestClientTest {

    private val config = FirebaseConfig(projectId = "proj1", apiKey = "key")

    /** A never-expiring session so these tests never actually hit the token-refresh path. */
    private fun freshTokenRefresher(): TokenRefresher {
        val dummyIdentityClient = IdentityToolkitClient(createHttpClient(MockEngine { error("not expected to be called") }), config)
        val refresher = TokenRefresher(dummyIdentityClient, NoOpSecretStore(), nowMillis = { 0L })
        refresher.setSession(TeacherSession("u", "d", "e", "tok", "r", Long.MAX_VALUE))
        return refresher
    }

    @Test
    fun `getDocument requests the expected path`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                """{"name":"projects/proj1/databases/(default)/documents/matns/m1","fields":{},"updateTime":"2026-01-01T00:00:00Z"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = FirestoreRestClient(createHttpClient(engine), config, freshTokenRefresher())

        val result = client.getDocument("matns/m1")

        assertTrue(capturedUrl!!.contains("/projects/proj1/databases/(default)/documents/matns/m1"))
        assertTrue(result is Resource.Success)
    }

    @Test
    fun `patchDocument sends the full document and the updateTime precondition`() = runTest {
        var capturedUrl: String? = null
        var capturedMethod: HttpMethod? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedMethod = request.method
            capturedBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """{"name":"projects/proj1/databases/(default)/documents/matns/m1","fields":{},"updateTime":"2026-01-02T00:00:00Z"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = FirestoreRestClient(createHttpClient(engine), config, freshTokenRefresher())

        val fields = mapOf("title" to FirestoreValue.StringValue("Title"))
        val result = client.patchDocument("matns/m1", fields, updateTimePrecondition = "2026-01-01T00:00:00Z")

        assertEquals(HttpMethod.Patch, capturedMethod)
        assertTrue(capturedUrl!!.contains("currentDocument.updateTime"))
        assertTrue(capturedBody!!.contains("\"title\""))
        assertTrue(result is Resource.Success)
        assertEquals("2026-01-02T00:00:00Z", (result as Resource.Success).data.updateTime)
    }

    @Test
    fun `a 400 FAILED_PRECONDITION response maps to Conflict`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                """{"error":{"code":400,"message":"the stored version is stale","status":"FAILED_PRECONDITION"}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = FirestoreRestClient(createHttpClient(engine), config, freshTokenRefresher())

        val result = client.patchDocument("matns/m1", emptyMap(), updateTimePrecondition = "stale-token")

        assertEquals(Resource.Failure(RemoteError.Conflict), result)
    }

    @Test
    fun `a save with a stale updateTime yields Conflict and does not retry automatically`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respond(
                """{"error":{"code":400,"message":"the stored version is stale","status":"FAILED_PRECONDITION"}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = FirestoreRestClient(createHttpClient(engine), config, freshTokenRefresher())

        val result = client.patchDocument("matns/m1", mapOf("title" to FirestoreValue.StringValue("T")), updateTimePrecondition = "stale-token")

        assertEquals(Resource.Failure(RemoteError.Conflict), result)
        assertEquals(1, requestCount)
    }
}
