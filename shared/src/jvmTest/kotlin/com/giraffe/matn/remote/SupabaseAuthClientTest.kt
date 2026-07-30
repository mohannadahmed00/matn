package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TOKEN_BODY = """
{
  "access_token": "access-1",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "refresh-1",
  "user": {
    "id": "uid1",
    "email": "teacher@example.com",
    "user_metadata": { "display_name": "Teacher One" }
  }
}
"""

class SupabaseAuthClientTest {

    private val config = SupabaseConfig(projectUrl = "https://p.supabase.co", anonKey = "anon-key", bucket = "matn-content")

    private fun clientRecording(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = TOKEN_BODY,
        requests: MutableList<HttpRequestData> = mutableListOf(),
    ): Pair<SupabaseAuthClient, MutableList<HttpRequestData>> {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return SupabaseAuthClient(createHttpClient(engine), config) to requests
    }

    @Test
    fun `sign-in posts the password grant with the apikey header`() = runTest {
        val (client, requests) = clientRecording()

        val result = client.signInWithPassword("teacher@example.com", "hunter2")

        val request = requests.single()
        assertEquals("https://p.supabase.co/auth/v1/token", request.url.toString().substringBefore('?'))
        assertEquals("password", request.url.parameters["grant_type"])
        assertEquals("anon-key", request.headers["apikey"])
        assertTrue(result is Resource.Success)
        assertEquals("uid1", result.data.uid)
        assertEquals("access-1", result.data.accessToken)
        assertEquals("refresh-1", result.data.refreshToken)
    }

    @Test
    fun `the refresh grant carries the user object so displayName survives a restore`() = runTest {
        val (client, requests) = clientRecording()

        val result = client.refresh("refresh-0")

        assertEquals("refresh_token", requests.single().url.parameters["grant_type"])
        assertTrue(result is Resource.Success)
        // The Firebase Secure Token API returned neither of these on a refresh; Supabase does.
        assertEquals("Teacher One", result.data.displayName)
        assertEquals("teacher@example.com", result.data.email)
    }

    @Test
    fun `an account with no display_name metadata falls back to the email local part`() = runTest {
        val (client, _) = clientRecording(
            body = """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"uid1","email":"teacher@example.com"}}""",
        )

        val result = client.signInWithPassword("teacher@example.com", "hunter2")

        assertTrue(result is Resource.Success)
        assertEquals("teacher", result.data.displayName)
    }

    @Test
    fun `expires_in becomes an absolute expiry in the future`() = runTest {
        val (client, _) = clientRecording()

        val result = client.signInWithPassword("teacher@example.com", "hunter2")

        assertTrue(result is Resource.Success)
        assertTrue(result.data.accessTokenExpiresAt > System.currentTimeMillis())
    }

    @Test
    fun `invalid_credentials is Unauthorized`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.BadRequest,
            body = """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""",
        )

        assertEquals(Resource.Failure(RemoteError.Unauthorized), client.signInWithPassword("t@example.com", "wrong"))
    }

    /** The OAuth-style shape an older or self-hosted GoTrue still returns. */
    @Test
    fun `invalid_grant is Unauthorized`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.BadRequest,
            body = """{"error":"invalid_grant","error_description":"Invalid Refresh Token"}""",
        )

        assertEquals(Resource.Failure(RemoteError.Unauthorized), client.refresh("stale"))
    }

    @Test
    fun `a banned user is Forbidden`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.Forbidden,
            body = """{"code":403,"error_code":"user_banned","msg":"User is banned"}""",
        )

        assertEquals(Resource.Failure(RemoteError.Forbidden), client.signInWithPassword("t@example.com", "hunter2"))
    }

    @Test
    fun `rate limiting is retryable`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.TooManyRequests,
            body = """{"code":429,"error_code":"over_request_rate_limit","msg":"Too many requests"}""",
        )

        val result = client.signInWithPassword("t@example.com", "hunter2")

        assertEquals(Resource.Failure(RemoteError.Server), result)
        assertTrue(RemoteError.Server.retryable)
    }
}
