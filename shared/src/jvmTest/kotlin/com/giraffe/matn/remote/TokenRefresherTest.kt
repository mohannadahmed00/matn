package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.identity.IdentityToolkitClient
import com.giraffe.matn.data.remote.identity.TokenRefresher
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
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

private class FakeSecretStore : SecretStore {
    private val store = mutableMapOf<String, String>()
    override val isProtected: Boolean = true
    override suspend fun put(key: String, value: String): Resource<Unit> {
        store[key] = value
        return Resource.Success(Unit)
    }
    override suspend fun get(key: String): Resource<String?> = Resource.Success(store[key])
    override suspend fun clear(key: String): Resource<Unit> {
        store.remove(key)
        return Resource.Success(Unit)
    }
}

class TokenRefresherTest {

    private val config = FirebaseConfig(projectId = "p", apiKey = "key")

    private fun refreshSuccessBody(idToken: String = "new-id-token", refreshToken: String = "new-refresh-token") =
        """{"id_token":"$idToken","refresh_token":"$refreshToken","expires_in":"3600","user_id":"uid1"}"""

    private val initialSession = TeacherSession(
        uid = "uid1",
        displayName = "Teacher",
        email = "t@example.com",
        idToken = "old-id-token",
        refreshToken = "old-refresh-token",
        idTokenExpiresAt = 1_000_000L,
    )

    @Test
    fun `refresh happens inside the 5-minute pre-expiry window`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(refreshSuccessBody(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = IdentityToolkitClient(createHttpClient(engine), config)
        val now = initialSession.idTokenExpiresAt - 4 * 60 * 1000L // 4 minutes to expiry — inside the window.
        val refresher = TokenRefresher(client, FakeSecretStore(), nowMillis = { now })
        refresher.setSession(initialSession)

        val result = refresher.currentIdToken()

        assertEquals(1, callCount)
        assertEquals(Resource.Success("new-id-token"), result)
    }

    @Test
    fun `no refresh when the token is not near expiry`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(refreshSuccessBody(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = IdentityToolkitClient(createHttpClient(engine), config)
        val now = initialSession.idTokenExpiresAt - 30 * 60 * 1000L // 30 minutes to expiry — outside the window.
        val refresher = TokenRefresher(client, FakeSecretStore(), nowMillis = { now })
        refresher.setSession(initialSession)

        val result = refresher.currentIdToken()

        assertEquals(0, callCount)
        assertEquals(Resource.Success("old-id-token"), result)
    }

    @Test
    fun `two concurrent callers cause exactly one refresh`() = runTest {
        val callCount = AtomicInteger(0)
        val engine = MockEngine {
            callCount.incrementAndGet()
            respond(refreshSuccessBody(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = IdentityToolkitClient(createHttpClient(engine), config)
        val now = initialSession.idTokenExpiresAt - 4 * 60 * 1000L
        val refresher = TokenRefresher(client, FakeSecretStore(), nowMillis = { now })
        refresher.setSession(initialSession)

        val first = async { refresher.currentIdToken() }
        val second = async { refresher.currentIdToken() }
        val results = listOf(first.await(), second.await())

        assertEquals(1, callCount.get())
        assertTrue(results.all { it == Resource.Success("new-id-token") })
    }

    @Test
    fun `INVALID_REFRESH_TOKEN clears the SecretStore and yields Unauthorized`() = runTest {
        val engine = MockEngine {
            respond(
                """{"error":{"code":400,"message":"INVALID_REFRESH_TOKEN","errors":[]}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = IdentityToolkitClient(createHttpClient(engine), config)
        val secretStore = FakeSecretStore()
        secretStore.put(TokenRefresher.REFRESH_TOKEN_KEY, "old-refresh-token")
        val now = initialSession.idTokenExpiresAt - 4 * 60 * 1000L
        val refresher = TokenRefresher(client, secretStore, nowMillis = { now })
        refresher.setSession(initialSession)

        val result = refresher.currentIdToken()

        assertEquals(Resource.Failure(RemoteError.Unauthorized), result)
        assertNull((secretStore.get(TokenRefresher.REFRESH_TOKEN_KEY) as Resource.Success).data)
        assertNull(refresher.session.value)
    }
}
