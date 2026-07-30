package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.postgrest.PostgrestFilter
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * FR-042/SC-008: the row-level-security half of the case matrix from `contracts/security-rules.md`
 * §3, driven against a real local Supabase stack (research D9) — the policies are the only gate on
 * student visibility, so they are exercised, not just reviewed. Every test skips via
 * [requireLocalStack] when the stack env vars are unset, so an ordinary `./gradlew test` stays
 * green with no Supabase CLI installed; `.github/workflows/rls-policy-tests.yml` is what runs it.
 *
 * **Anonymous cases** issue a bare request carrying only the `apikey` header — [PostgrestClient] is
 * a teacher-only client that always attaches a bearer token, so an anonymous call has to bypass it.
 * **Teacher and "authenticated non-teacher" cases go through the real client**, primed with a real
 * session, which is what exercises the exact request shape the app sends.
 *
 * One deliberate difference from the retired Firestore rules: RLS *filters* rather than *refuses*.
 * Where Firestore answered 403 to a read of a draft, Postgres answers 200 with the row absent. No
 * data leaks either way; the assertions below say so explicitly wherever it applies.
 *
 * Setup seeds the `teachers` marker with the service-role key, which bypasses RLS — no client,
 * including the teacher's own, may write that table (case W9 proves it).
 */
@OptIn(ExperimentalUuidApi::class)
class RlsPolicyTest {

    private lateinit var stack: LocalStack
    private lateinit var config: SupabaseConfig
    private lateinit var httpClient: HttpClient
    private lateinit var authClient: SupabaseAuthClient
    private lateinit var teacherSession: TeacherSession
    private lateinit var nonTeacherSession: TeacherSession

    @BeforeTest
    fun setUp() = runTest {
        stack = requireLocalStack()
        config = stack.config()
        httpClient = createHttpClient()
        authClient = SupabaseAuthClient(httpClient, config)
        teacherSession = signUpFreshUser()
        nonTeacherSession = signUpFreshUser()
        seedTeacherMarker(teacherSession.uid)
    }

    private suspend fun signUpFreshUser(): TeacherSession {
        val email = "user-${Uuid.random()}@example.com"
        val response = httpClient.post("${config.authBaseUrl}/signup") {
            headers { append("apikey", config.anonKey) }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("email", email); put("password", "Password123!") })
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return TeacherSession(
            uid = body.getValue("user").jsonObject.getValue("id").jsonPrimitive.content,
            displayName = "",
            email = email,
            accessToken = body.getValue("access_token").jsonPrimitive.content,
            refreshToken = body.getValue("refresh_token").jsonPrimitive.content,
            accessTokenExpiresAt = Long.MAX_VALUE,
        )
    }

    private suspend fun seedTeacherMarker(uid: String) {
        httpClient.post("${config.restBaseUrl}/teachers") {
            headers {
                append("apikey", stack.serviceRoleKey)
                append(HttpHeaders.Authorization, "Bearer ${stack.serviceRoleKey}")
            }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("uid", uid); put("display_name", "Test Teacher") })
        }
    }

    private fun clientFor(session: TeacherSession?): PostgrestClient =
        PostgrestClient(httpClient, config, primedRefresher(authClient, session))

    // --- anonymous helpers: apikey only, never an Authorization header ---

    private suspend fun anonymousGet(path: String): HttpResponse =
        httpClient.get("${config.restBaseUrl}/$path") {
            headers { append("apikey", config.anonKey) }
        }

    private suspend fun anonymousPost(path: String, row: JsonObject): HttpResponse =
        httpClient.post("${config.restBaseUrl}/$path") {
            headers { append("apikey", config.anonKey) }
            contentType(ContentType.Application.Json)
            setBody(row)
        }

    private suspend fun anonymousPatch(path: String, row: JsonObject): HttpResponse =
        httpClient.patch("${config.restBaseUrl}/$path") {
            headers {
                append("apikey", config.anonKey)
                append("Prefer", "return=representation")
            }
            contentType(ContentType.Application.Json)
            setBody(row)
        }

    private suspend fun HttpResponse.rows(): List<JsonObject> =
        Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject }

    private fun matnRow(id: String, published: Boolean) = buildJsonObject {
        put("id", id)
        put("title", "Test")
        put("published", published)
    }

    private suspend fun createMatn(published: Boolean): String {
        val id = "m-${Uuid.random()}"
        val created = clientFor(teacherSession).insert("matns", matnRow(id, published))
        assertTrue(created is Resource.Success, "teacher create failed: $created")
        return id
    }

    // ---- R: reads ----

    @Test
    fun `R1 anonymous read of a published matn is allowed`() = runTest {
        val matnId = createMatn(published = true)

        val rows = anonymousGet("matns?id=eq.$matnId&select=id,title").rows()

        assertEquals(1, rows.size)
    }

    @Test
    fun `R2 anonymous read of a draft matn returns nothing`() = runTest {
        val matnId = createMatn(published = false)

        val rows = anonymousGet("matns?id=eq.$matnId&select=id,title").rows()

        assertTrue(rows.isEmpty(), "a draft leaked to an anonymous caller: $rows")
    }

    @Test
    fun `R3 an anonymous listing of the whole table yields published rows only`() = runTest {
        val published = createMatn(published = true)
        val draft = createMatn(published = false)

        val ids = anonymousGet("matns?select=id,published").rows()
            .map { it.getValue("id").jsonPrimitive.content }

        assertTrue(published in ids)
        assertTrue(draft !in ids, "a draft leaked into an unfiltered anonymous listing")
    }

    @Test
    fun `R5 teacher read of a draft matn is allowed`() = runTest {
        val matnId = createMatn(published = false)

        val result = clientFor(teacherSession).select("matns", listOf("id"), listOf(PostgrestFilter("id", "eq.$matnId")))

        assertTrue(result is Resource.Success && result.data.size == 1, "teacher could not read own draft: $result")
    }

    @Test
    fun `R6 an authenticated non-teacher cannot see a draft matn`() = runTest {
        val matnId = createMatn(published = false)

        val result = clientFor(nonTeacherSession).select("matns", listOf("id"), listOf(PostgrestFilter("id", "eq.$matnId")))

        assertTrue(result is Resource.Success && result.data.isEmpty(), "a draft leaked to a non-teacher: $result")
    }

    @Test
    fun `R7 the teachers marker is invisible to anonymous and authenticated callers alike`() = runTest {
        assertTrue(anonymousGet("teachers?select=uid").rows().isEmpty())

        val asTeacher = clientFor(teacherSession).select("teachers", listOf("uid"))

        // Not even the teacher whose own row it is: the table has no policy at all.
        assertTrue(asTeacher is Resource.Success && asTeacher.data.isEmpty(), "the teachers marker was readable: $asTeacher")
    }

    // ---- W: writes ----

    @Test
    fun `W1 anonymous create is denied`() = runTest {
        val response = anonymousPost("matns", matnRow("m-${Uuid.random()}", published = false))

        assertTrue(response.status.value in setOf(401, 403), "expected 401/403, got ${response.status.value}: ${response.bodyAsText()}")
    }

    @Test
    fun `W2 anonymous update of a published matn changes nothing`() = runTest {
        val matnId = createMatn(published = true)

        val response = anonymousPatch("matns?id=eq.$matnId", buildJsonObject { put("title", "Hijacked") })

        assertTrue(
            response.status.value in setOf(401, 403) || response.rows().isEmpty(),
            "an anonymous update landed: ${response.status.value} ${response.bodyAsText()}",
        )
        val title = clientFor(teacherSession)
            .select("matns", listOf("title"), listOf(PostgrestFilter("id", "eq.$matnId")))
        assertTrue(title is Resource.Success)
        assertEquals("Test", title.data.single().getValue("title").jsonPrimitive.content)
    }

    @Test
    fun `W3 anonymous flip to published changes nothing`() = runTest {
        val matnId = createMatn(published = false)

        anonymousPatch("matns?id=eq.$matnId", buildJsonObject { put("published", true) })

        assertTrue(anonymousGet("matns?id=eq.$matnId&select=id").rows().isEmpty(), "a draft was published anonymously")
    }

    @Test
    fun `W5 anonymous delete is denied`() = runTest {
        val matnId = createMatn(published = false)

        val response = httpClient.delete("${config.restBaseUrl}/matns?id=eq.$matnId") {
            headers { append("apikey", config.anonKey) }
        }

        assertTrue(response.status.value in setOf(401, 403) || response.status.value in 200..299)
        val stillThere = clientFor(teacherSession)
            .select("matns", listOf("id"), listOf(PostgrestFilter("id", "eq.$matnId")))
        assertTrue(stillThere is Resource.Success && stillThere.data.size == 1, "an anonymous delete landed")
    }

    @Test
    fun `W6 an authenticated non-teacher write is Forbidden`() = runTest {
        val result = clientFor(nonTeacherSession).insert("matns", matnRow("m-${Uuid.random()}", published = false))

        assertEquals(Resource.Failure(RemoteError.Forbidden), result)
    }

    @Test
    fun `W7 teacher create, update, publish and unpublish are allowed`() = runTest {
        val client = clientFor(teacherSession)
        val matnId = "m-${Uuid.random()}"

        val created = client.insert("matns", matnRow(matnId, published = false))
        assertTrue(created is Resource.Success, "create failed: $created")
        val revision = created.data.single().getValue("revision").jsonPrimitive.content

        val published = client.update(
            table = "matns",
            row = buildJsonObject { put("published", true) },
            filters = listOf(PostgrestFilter("id", "eq.$matnId"), PostgrestFilter("revision", "eq.$revision")),
        )
        assertTrue(published is Resource.Success && published.data.isNotEmpty(), "publish failed (revision=$revision): $published")

        val nextRevision = published.data.single().getValue("revision").jsonPrimitive.content
        assertTrue(nextRevision != revision, "the revision trigger did not fire")

        val unpublished = client.update(
            table = "matns",
            row = buildJsonObject { put("published", false) },
            filters = listOf(PostgrestFilter("id", "eq.$matnId"), PostgrestFilter("revision", "eq.$nextRevision")),
        )
        assertTrue(unpublished is Resource.Success && unpublished.data.isNotEmpty(), "unpublish failed: $unpublished")
    }

    @Test
    fun `W8 a stale revision precondition matches no row`() = runTest {
        val matnId = createMatn(published = false)

        val result = clientFor(teacherSession).update(
            table = "matns",
            row = buildJsonObject { put("title", "Second writer") },
            filters = listOf(PostgrestFilter("id", "eq.$matnId"), PostgrestFilter("revision", "eq.9999")),
        )

        assertTrue(result is Resource.Success && result.data.isEmpty(), "a stale revision was accepted: $result")
    }

    @Test
    fun `W9 self-promotion by writing the teachers marker is denied`() = runTest {
        val anonymous = anonymousPost("teachers", buildJsonObject { put("uid", nonTeacherSession.uid) })
        assertTrue(anonymous.status.value in setOf(401, 403), "anonymous self-promotion: ${anonymous.status.value}")

        val authenticated = clientFor(nonTeacherSession)
            .insert("teachers", buildJsonObject { put("uid", nonTeacherSession.uid) })
        assertEquals(Resource.Failure(RemoteError.Forbidden), authenticated)
    }

    // ---- SC-011: atomicity of a large in-flight save ----

    @Test
    fun `a row read during a large save never returns a mixture of two versions`() = runTest {
        val client = clientFor(teacherSession)
        val matnId = "m-${Uuid.random()}"
        val created = client.insert("matns", buildJsonObject { put("id", matnId); put("title", "v1") })
        assertTrue(created is Resource.Success, "initial create failed: $created")
        val revision = created.data.single().getValue("revision").jsonPrimitive.content

        val saved = client.update(
            table = "matns",
            row = buildJsonObject { put("title", "v2"); put("description", "x".repeat(400_000)) },
            filters = listOf(PostgrestFilter("id", "eq.$matnId"), PostgrestFilter("revision", "eq.$revision")),
        )
        assertTrue(saved is Resource.Success && saved.data.isNotEmpty(), "large save failed: $saved")

        val read = client.select("matns", listOf("title", "description"), listOf(PostgrestFilter("id", "eq.$matnId")))
        assertTrue(read is Resource.Success)
        val row = read.data.single()
        assertEquals("v2", row.getValue("title").jsonPrimitive.content)
        assertEquals(400_000, row.getValue("description").jsonPrimitive.content.length)
    }
}
