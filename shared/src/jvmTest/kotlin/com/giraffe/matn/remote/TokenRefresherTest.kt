package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.auth.TokenRefresher
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenRefresherTest {

    private val config = SupabaseConfig(projectUrl = "https://p.supabase.co", anonKey = "anon-key", bucket = "matn-content")

    private fun refreshSuccessBody(accessToken: String = "new-access-token", refreshToken: String = "new-refresh-token") =
        """{"access_token":"$accessToken","refresh_token":"$refreshToken","expires_in":3600,
           "user":{"id":"uid1","email":"t@example.com","user_metadata":{"display_name":"Teacher"}}}"""

    private val initialSession = TeacherSession(
        uid = "uid1",
        displayName = "Teacher",
        email = "t@example.com",
        accessToken = "old-access-token",
        refreshToken = "old-refresh-token",
        accessTokenExpiresAt = 1_000_000L,
    )

    private fun clientCounting(counter: AtomicInteger, body: String = refreshSuccessBody()): SupabaseAuthClient {
        val engine = MockEngine {
            counter.incrementAndGet()
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return SupabaseAuthClient(createHttpClient(engine), config)
    }

    @Test
    fun `refresh happens inside the 5-minute pre-expiry window`() = runTest {
        val calls = AtomicInteger(0)
        val client = clientCounting(calls)
        val now = initialSession.accessTokenExpiresAt - 4 * 60 * 1000L // 4 minutes to expiry — inside the window.
        val refresher = TokenRefresher(client, FakeSecretStore(), nowMillis = { now })
        refresher.setSession(initialSession)

        val result = refresher.currentAccessToken()

        assertEquals(1, calls.get())
        assertEquals(Resource.Success("new-access-token"), result)
    }

    @Test
    fun `no refresh when the token is not near expiry`() = runTest {
        val calls = AtomicInteger(0)
        val client = clientCounting(calls)
        val now = initialSession.accessTokenExpiresAt - 30 * 60 * 1000L // 30 minutes to expiry — outside the window.
        val refresher = TokenRefresher(client, FakeSecretStore(), nowMillis = { now })
        refresher.setSession(initialSession)

        val result = refresher.currentAccessToken()

        assertEquals(0, calls.get())
        assertEquals(Resource.Success("old-access-token"), result)
    }

    @Test
    fun `two concurrent callers cause exactly one refresh`() = runTest {
        val calls = AtomicInteger(0)
        val client = clientCounting(calls)
        val now = initialSession.accessTokenExpiresAt - 4 * 60 * 1000L
        val refresher = TokenRefresher(client, FakeSecretStore(), nowMillis = { now })
        refresher.setSession(initialSession)

        val first = async { refresher.currentAccessToken() }
        val second = async { refresher.currentAccessToken() }
        val results = listOf(first.await(), second.await())

        assertEquals(1, calls.get())
        assertTrue(results.all { it == Resource.Success("new-access-token") })
    }

    /** Supabase rotates the refresh token on every use; keeping the old one would break the next
     * restore, so the rotated value has to reach the [com.giraffe.matn.domain.secret.SecretStore]. */
    @Test
    fun `the rotated refresh token replaces the stored one`() = runTest {
        val secretStore = FakeSecretStore()
        secretStore.put(TokenRefresher.REFRESH_TOKEN_KEY, "old-refresh-token")
        val client = clientCounting(AtomicInteger(0))
        val now = initialSession.accessTokenExpiresAt - 4 * 60 * 1000L
        val refresher = TokenRefresher(client, secretStore, nowMillis = { now })
        refresher.setSession(initialSession)

        refresher.currentAccessToken()

        assertEquals("new-refresh-token", (secretStore.get(TokenRefresher.REFRESH_TOKEN_KEY) as Resource.Success).data)
        assertEquals("new-refresh-token", refresher.session.value?.refreshToken)
    }

    @Test
    fun `a rejected refresh token clears the SecretStore and yields Unauthorized`() = runTest {
        val engine = MockEngine {
            respond(
                """{"code":400,"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token"}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = SupabaseAuthClient(createHttpClient(engine), config)
        val secretStore = FakeSecretStore()
        secretStore.put(TokenRefresher.REFRESH_TOKEN_KEY, "old-refresh-token")
        val now = initialSession.accessTokenExpiresAt - 4 * 60 * 1000L
        val refresher = TokenRefresher(client, secretStore, nowMillis = { now })
        refresher.setSession(initialSession)

        val result = refresher.currentAccessToken()

        assertEquals(Resource.Failure(RemoteError.Unauthorized), result)
        assertNull((secretStore.get(TokenRefresher.REFRESH_TOKEN_KEY) as Resource.Success).data)
        assertNull(refresher.session.value)
    }
}
