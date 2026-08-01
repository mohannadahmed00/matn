package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageRestClientAudioTest {

    private val config = SupabaseConfig(projectUrl = "https://p.supabase.co", anonKey = "anon-key", bucket = "matn-content")

    private val session = TeacherSession(
        uid = "uid1",
        displayName = "Teacher",
        email = "t@example.com",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        accessTokenExpiresAt = Long.MAX_VALUE,
    )

    private fun client(
        onRequest: (HttpRequestData) -> Unit = {},
        respondWith: (HttpRequestData) -> Pair<HttpStatusCode, ByteArray>,
    ): StorageRestClient {
        val engine = MockEngine { request ->
            onRequest(request)
            val (status, body) = respondWith(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val httpClient = createHttpClient(engine)
        val refresher = primedRefresher(SupabaseAuthClient(httpClient, config), session)
        return StorageRestClient(httpClient, config, refresher)
    }

    @Test
    fun `download returns bytes`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        var seenMethod: HttpMethod? = null
        val storage = client(onRequest = { seenMethod = it.method }) { HttpStatusCode.OK to bytes }

        val result = storage.download("matns/m1/verses/v1-tag.mp3")

        assertTrue(result is Resource.Success)
        assertEquals(listOf<Byte>(1, 2, 3, 4), result.data.toList())
        assertEquals(HttpMethod.Get, seenMethod)
    }

    @Test
    fun `a 404 on download maps to a failure`() = runTest {
        val storage = client { HttpStatusCode.NotFound to "not found".encodeToByteArray() }

        val result = storage.download("matns/m1/verses/missing.mp3")

        assertTrue(result is Resource.Failure)
    }

    @Test
    fun `delete issues DELETE with the bearer token`() = runTest {
        var seenRequest: HttpRequestData? = null
        val storage = client(onRequest = { seenRequest = it }) { HttpStatusCode.OK to "{}".encodeToByteArray() }

        val result = storage.delete("matns/m1/verses/v1-tag.mp3")

        assertTrue(result is Resource.Success)
        assertEquals(HttpMethod.Delete, seenRequest?.method)
        assertEquals("Bearer access-1", seenRequest?.headers?.get(HttpHeaders.Authorization))
        assertEquals("anon-key", seenRequest?.headers?.get("apikey"))
    }

    @Test
    fun `listWithSizes pages through two pages and merges them`() = runTest {
        var page = 0
        val storage = client {
            // A full 100-item page forces the client to fetch a second page.
            val body = if (page == 0) {
                (0 until 100).joinToString(prefix = "[", postfix = "]") { i ->
                    """{"name":"v$i-a.mp3","metadata":{"size":10}}"""
                }
            } else {
                """[{"name":"v2-b.mp3","metadata":{"size":200}}]"""
            }
            page++
            HttpStatusCode.OK to body.encodeToByteArray()
        }

        val result = storage.listWithSizes("matns/m1/verses/")

        assertTrue(result is Resource.Success)
        assertEquals(101, result.data.size)
        assertEquals(10L, result.data["matns/m1/verses/v0-a.mp3"])
        assertEquals(200L, result.data["matns/m1/verses/v2-b.mp3"])
    }

    @Test
    fun `a 413 maps to RemoteError QuotaExceeded`() = runTest {
        val storage = client { HttpStatusCode.PayloadTooLarge to "too large".encodeToByteArray() }

        val result = storage.delete("matns/m1/verses/v1-tag.mp3")

        assertEquals(Resource.Failure(RemoteError.QuotaExceeded), result)
    }

    /**
     * Regression for a shipped bug. Before the bucket's `allowed_mime_types` included `audio/mpeg`,
     * every verse upload came back exactly like this — and the 400 fell through the mapper's `else`
     * to `Decode`, so the teacher was told "unexpected response, please report this" about a
     * response that was neither unexpected nor malformed. A refusal must read as a refusal.
     */
    @Test
    fun `a 400 invalid mime type maps to RemoteError Rejected, not Decode`() = runTest {
        val body = """{"statusCode":"400","error":"invalid_mime_type","message":"mime type audio/mpeg is not supported"}"""
        val storage = client { HttpStatusCode.BadRequest to body.encodeToByteArray() }

        val result = storage.upload("matns/m1/verses/v1-tag.mp3", byteArrayOf(1, 2), "audio/mpeg")

        assertEquals(Resource.Failure(RemoteError.Rejected), result)
    }
}
