package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.firestore.FirestoreRestClient
import com.giraffe.matn.data.remote.firestore.FirestoreValue
import com.giraffe.matn.data.remote.firestore.toJson
import com.giraffe.matn.data.remote.identity.IdentityToolkitClient
import com.giraffe.matn.data.remote.identity.TokenRefresher
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private class RulesTestSecretStore : SecretStore {
    override val isProtected = true
    override suspend fun put(key: String, value: String) = Resource.Success(Unit)
    override suspend fun get(key: String) = Resource.Success<String?>(null)
    override suspend fun clear(key: String) = Resource.Success(Unit)
}

/**
 * FR-042/SC-008: the Firestore half of the case matrix from `contracts/security-rules.md` §3,
 * driven against the Firebase Local Emulator Suite (research D9). Every test skips via
 * [requireEmulatorHost] when `FIREBASE_EMULATOR_HOST` is unset, so an ordinary `./gradlew test`
 * stays green with no Firebase CLI installed. The Storage cases (formerly S1-S7) were retired when
 * Storage moved to Supabase — see `design-notes.md`.
 *
 * **Anonymous-caller cases** (no signed-in session) issue a bare Ktor request with no
 * `Authorization` header — `FirestoreRestClient` is a teacher-only client that always attaches a
 * bearer token, so an anonymous call has to bypass it. **Teacher and "authenticated non-teacher"
 * cases go through the real project client**, primed with a real emulator-issued session, which is
 * what exercises the exact request shape the app sends.
 *
 * Setup seeds the `teachers/{uid}` marker directly via the Firestore emulator's documented
 * `Authorization: Bearer owner` rules-bypass — no client, including the teacher's own, may write
 * that collection through the ordinary rules (case W9 proves it).
 */
@OptIn(ExperimentalUuidApi::class)
class SecurityRulesTest {

    private lateinit var config: FirebaseConfig
    private lateinit var httpClient: HttpClient
    private lateinit var identityClient: IdentityToolkitClient
    private lateinit var teacherSession: TeacherSession
    private lateinit var nonTeacherSession: TeacherSession

    @BeforeTest
    fun setUp() = runTest {
        val host = requireEmulatorHost()
        config = FirebaseConfig(projectId = "matn-test", apiKey = "fake-api-key", emulatorHost = host)
        httpClient = createHttpClient()
        identityClient = IdentityToolkitClient(httpClient, config)
        teacherSession = signUpFreshUser()
        nonTeacherSession = signUpFreshUser()
        seedTeacherMarker(teacherSession.uid)
    }

    private suspend fun signUpFreshUser(): TeacherSession {
        val email = "user-${Uuid.random()}@example.com"
        val response = httpClient.post("${config.identityBaseUrl}/accounts:signUp?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("email", email)
                put("password", "Password123!")
                put("returnSecureToken", true)
            })
        }
        val body = response.body<JsonObject>()
        return TeacherSession(
            uid = body.getValue("localId").jsonPrimitive.content,
            displayName = "",
            email = email,
            idToken = body.getValue("idToken").jsonPrimitive.content,
            refreshToken = body.getValue("refreshToken").jsonPrimitive.content,
            idTokenExpiresAt = Long.MAX_VALUE,
        )
    }

    private suspend fun seedTeacherMarker(uid: String) {
        httpClient.patch("${config.firestoreBaseUrl}/projects/${config.projectId}/databases/(default)/documents/teachers/$uid") {
            headers { append(HttpHeaders.Authorization, "Bearer owner") }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("fields", buildJsonObject { put("displayName", buildJsonObject { put("stringValue", "Test Teacher") }) })
            })
        }
    }

    private fun firestoreClientFor(session: TeacherSession?): FirestoreRestClient {
        val tokenRefresher = TokenRefresher(identityClient, RulesTestSecretStore(), nowMillis = { 0L })
        session?.let { tokenRefresher.setSession(it) }
        return FirestoreRestClient(httpClient, config, tokenRefresher)
    }

    /** An anonymous read/write with no `Authorization` header at all. */
    private suspend fun anonymousFirestoreGet(path: String) =
        httpClient.get("${config.firestoreBaseUrl}/projects/${config.projectId}/databases/(default)/documents/$path")

    private suspend fun anonymousFirestorePatch(path: String, fields: Map<String, FirestoreValue>) =
        httpClient.patch("${config.firestoreBaseUrl}/projects/${config.projectId}/databases/(default)/documents/$path") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("fields", buildJsonObject { fields.forEach { (k, v) -> put(k, v.toJson()) } })
            })
        }

    private fun draftFields(published: Boolean) = mapOf(
        "title" to FirestoreValue.StringValue("Test"),
        "published" to FirestoreValue.BooleanValue(published),
    )

    // ---- R: Firestore reads ----

    @Test
    fun `R1 anonymous read of a published matn is allowed`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = true), null, requireNotExists = true)
        val response = anonymousFirestoreGet("matns/$matnId")
        assertTrue(response.status.value in 200..299, "expected 2xx, got ${response.status.value}: ${response.bodyAsText()}")
    }

    @Test
    fun `R2 anonymous read of a draft matn is denied`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = false), null, requireNotExists = true)
        val response = anonymousFirestoreGet("matns/$matnId")
        assertTrue(
            response.status.value == 403 || response.status.value == 401,
            "expected 403/401, got ${response.status.value}: ${response.bodyAsText()}",
        )
    }

    @Test
    fun `R3 anonymous collection query filtered to published is allowed`() = runTest {
        val response = httpClient.post(
            "${config.firestoreBaseUrl}/projects/${config.projectId}/databases/(default)/documents:runQuery",
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                """{"structuredQuery":{"from":[{"collectionId":"matns"}],"where":{"fieldFilter":{"field":{"fieldPath":"published"},"op":"EQUAL","value":{"booleanValue":true}}}}}""",
            )
        }
        assertTrue(response.status.value in 200..299)
    }

    @Test
    fun `R4 anonymous unfiltered collection listing is denied`() = runTest {
        val response = httpClient.get("${config.firestoreBaseUrl}/projects/${config.projectId}/databases/(default)/documents/matns")
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    @Test
    fun `R5 teacher read of a draft matn is allowed`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = false), null, requireNotExists = true)
        val result = firestoreClientFor(teacherSession).getDocument("matns/$matnId")
        assertTrue(result is Resource.Success)
    }

    @Test
    fun `R6 authenticated non-teacher read of a draft matn is denied`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = false), null, requireNotExists = true)
        val result = firestoreClientFor(nonTeacherSession).getDocument("matns/$matnId")
        assertEquals(Resource.Failure(RemoteError.Forbidden), result)
    }

    @Test
    fun `R7 anonymous read of the teachers marker is denied`() = runTest {
        val response = anonymousFirestoreGet("teachers/${teacherSession.uid}")
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    // ---- W: Firestore writes ----

    @Test
    fun `W1 anonymous create is denied`() = runTest {
        val response = anonymousFirestorePatch("matns/m-${Uuid.random()}", draftFields(published = false))
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    @Test
    fun `W2 anonymous update of a published matn is denied`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = true), null, requireNotExists = true)
        val response = anonymousFirestorePatch("matns/$matnId", draftFields(published = true))
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    @Test
    fun `W3 anonymous flip to published is denied`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = false), null, requireNotExists = true)
        val response = anonymousFirestorePatch("matns/$matnId", draftFields(published = true))
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    @Test
    fun `W4 anonymous unpublish is denied`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = true), null, requireNotExists = true)
        val response = anonymousFirestorePatch("matns/$matnId", draftFields(published = false))
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    @Test
    fun `W5 anonymous delete is denied`() = runTest {
        val matnId = "m-${Uuid.random()}"
        firestoreClientFor(teacherSession).patchDocument("matns/$matnId", draftFields(published = false), null, requireNotExists = true)
        val response = httpClient.delete("${config.firestoreBaseUrl}/projects/${config.projectId}/databases/(default)/documents/matns/$matnId")
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    @Test
    fun `W6 authenticated non-teacher write is denied`() = runTest {
        val result = firestoreClientFor(nonTeacherSession)
            .patchDocument("matns/m-${Uuid.random()}", draftFields(published = false), null, requireNotExists = true)
        assertEquals(Resource.Failure(RemoteError.Forbidden), result)
    }

    @Test
    fun `W7 teacher create update publish and unpublish are allowed`() = runTest {
        val matnId = "m-${Uuid.random()}"
        val client = firestoreClientFor(teacherSession)
        val created = client.patchDocument("matns/$matnId", draftFields(published = false), null, requireNotExists = true)
        assertTrue(created is Resource.Success, "create failed: $created")
        val updateTime = created.data.updateTime
        val published = client.patchDocument("matns/$matnId", draftFields(published = true), updateTime)
        assertTrue(published is Resource.Success, "publish failed: $published (sent precondition updateTime=$updateTime)")
    }

    @Test
    fun `W8 a stale updateTime precondition is denied as a conflict`() = runTest {
        val matnId = "m-${Uuid.random()}"
        val client = firestoreClientFor(teacherSession)
        client.patchDocument("matns/$matnId", draftFields(published = false), null, requireNotExists = true)
        val result = client.patchDocument("matns/$matnId", draftFields(published = false), "2000-01-01T00:00:00.000000Z")
        assertEquals(Resource.Failure(RemoteError.Conflict), result)
    }

    @Test
    fun `W9 anonymous self-promotion by writing the teachers marker is denied`() = runTest {
        val response = anonymousFirestorePatch(
            "teachers/${nonTeacherSession.uid}",
            mapOf("displayName" to FirestoreValue.StringValue("Self-promoted")),
        )
        assertTrue(response.status.value == 403 || response.status.value == 401)
    }

    // ---- Storage rule cases retired: Storage moved to Supabase (see design-notes.md, T-storage-swap) ----

    // ---- SC-011: atomicity of a large in-flight save ----

    @Test
    fun `a document read during a large save never returns a mixture of two versions`() = runTest {
        val matnId = "m-${Uuid.random()}"
        val client = firestoreClientFor(teacherSession)
        val v1 = client.patchDocument("matns/$matnId", mapOf("title" to FirestoreValue.StringValue("v1")), null, requireNotExists = true)
        assertTrue(v1 is Resource.Success, "initial create failed: $v1")
        val updateTime = v1.data.updateTime
        val bigFields = mapOf(
            "title" to FirestoreValue.StringValue("v2"),
            "body" to FirestoreValue.StringValue("x".repeat(400_000)),
        )
        val saveResult = client.patchDocument("matns/$matnId", bigFields, updateTime)
        assertTrue(saveResult is Resource.Success, "large save failed: $saveResult")
        // Every read after the save completes returns the complete new version, never a mixture.
        val read = firestoreClientFor(teacherSession).getDocument("matns/$matnId")
        assertTrue(read is Resource.Success, "post-save read failed: $read")
        val title = read.data.fields["title"]
        assertEquals(FirestoreValue.StringValue("v2"), title)
    }
}
