package com.giraffe.matn.remote

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.postgrest.PostgrestFilter
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgrestClientTest {

    private val config = SupabaseConfig(projectUrl = "https://p.supabase.co", anonKey = "anon-key", bucket = "matn-content")

    private val session = TeacherSession(
        uid = "uid1",
        displayName = "Teacher",
        email = "t@example.com",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        accessTokenExpiresAt = Long.MAX_VALUE,
    )

    private fun clientRecording(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "[]",
        signedIn: Boolean = true,
    ): Pair<PostgrestClient, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val httpClient = createHttpClient(engine)
        // The auth client is never reached: the primed session is nowhere near expiry.
        val refresher = primedRefresher(SupabaseAuthClient(httpClient, config), session.takeIf { signedIn })
        return PostgrestClient(httpClient, config, refresher) to requests
    }

    @Test
    fun `select builds the column list, filters, and order, and sends both credentials`() = runTest {
        val (client, requests) = clientRecording(body = """[{"id":"m1","title":"Ajurrumiyya"}]""")

        val result = client.select(
            table = "matns",
            columns = listOf("id", "title"),
            filters = listOf(PostgrestFilter("id", "eq.m1")),
            order = "updated_at.desc",
        )

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/rest/v1/matns", request.url.encodedPath)
        assertEquals("id,title", request.url.parameters["select"])
        assertEquals("eq.m1", request.url.parameters["id"])
        assertEquals("updated_at.desc", request.url.parameters["order"])
        assertEquals("anon-key", request.headers["apikey"])
        assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
        assertTrue(result is Resource.Success)
        assertEquals("m1", result.data.single()["id"].toString().trim('"'))
    }

    @Test
    fun `insert asks for the representation back so the new revision arrives on the same round trip`() = runTest {
        val (client, requests) = clientRecording(
            status = HttpStatusCode.Created,
            body = """[{"id":"m1","revision":1}]""",
        )

        val result = client.insert("matns", buildJsonObject { put("id", "m1") })

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("return=representation", request.headers["Prefer"])
        assertEquals("""{"id":"m1"}""", String(request.body.toByteArray()))
        assertTrue(result is Resource.Success)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `update carries the revision precondition as a filter`() = runTest {
        val (client, requests) = clientRecording(body = """[{"id":"m1","revision":3}]""")

        client.update(
            table = "matns",
            row = buildJsonObject { put("id", "m1") },
            filters = listOf(PostgrestFilter("id", "eq.m1"), PostgrestFilter("revision", "eq.2")),
        )

        val request = requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("eq.m1", request.url.parameters["id"])
        assertEquals("eq.2", request.url.parameters["revision"])
    }

    /** PostgREST reports "the filters matched nothing" as 200 with an empty array, not an error —
     * the caller is what turns that into a conflict or a refusal. */
    @Test
    fun `an update that matches no row succeeds with an empty list`() = runTest {
        val (client, _) = clientRecording(body = "[]")

        val result = client.update("matns", buildJsonObject { put("id", "m1") }, listOf(PostgrestFilter("id", "eq.m1")))

        assertTrue(result is Resource.Success)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `a duplicate primary key is a Conflict`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.Conflict,
            body = """{"code":"23505","details":"Key (id)=(m1) already exists.","hint":null,"message":"duplicate key value violates unique constraint"}""",
        )

        assertEquals(
            Resource.Failure(RemoteError.Conflict),
            client.insert("matns", buildJsonObject { put("id", "m1") }),
        )
    }

    @Test
    fun `a row-level security violation is Forbidden`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.Forbidden,
            body = """{"code":"42501","details":null,"hint":null,"message":"new row violates row-level security policy for table \"matns\""}""",
        )

        assertEquals(
            Resource.Failure(RemoteError.Forbidden),
            client.insert("matns", buildJsonObject { put("id", "m1") }),
        )
    }

    @Test
    fun `an expired JWT is Unauthorized`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.Unauthorized,
            body = """{"code":"PGRST301","details":null,"hint":null,"message":"JWT expired"}""",
        )

        assertEquals(Resource.Failure(RemoteError.Unauthorized), client.select("matns", listOf("id")))
    }

    @Test
    fun `a 5xx is retryable`() = runTest {
        val (client, _) = clientRecording(status = HttpStatusCode.BadGateway, body = "")

        assertEquals(Resource.Failure(RemoteError.Server), client.select("matns", listOf("id")))
    }

    @Test
    fun `a missing table is NotFound`() = runTest {
        val (client, _) = clientRecording(
            status = HttpStatusCode.NotFound,
            body = """{"code":"42P01","message":"relation \"public.nope\" does not exist"}""",
        )

        assertEquals(Resource.Failure(AppError.NotFound), client.select("nope", listOf("id")))
    }

    @Test
    fun `no signed-in session short-circuits before any request is made`() = runTest {
        val (client, requests) = clientRecording(signedIn = false)

        assertEquals(Resource.Failure(RemoteError.Unauthorized), client.select("matns", listOf("id")))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a body that is not a JSON array is a Decode failure, not a network failure`() = runTest {
        val (client, _) = clientRecording(body = """{"id":"m1"}""")

        assertEquals(Resource.Failure(RemoteError.Decode), client.select("matns", listOf("id")))
    }
}
