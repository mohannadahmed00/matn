package com.giraffe.matn.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.auth.TokenRefresher
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.data.repository.SupabaseCatalogRepository
import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.UploadProgress
import com.giraffe.matn.domain.audio.VerseAudioPlan
import com.giraffe.matn.domain.audio.VerseAudioUploader
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.secret.SecretStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeSecretStore : SecretStore {
    override val isProtected: Boolean = true
    override suspend fun put(key: String, value: String): Resource<Unit> = Resource.Success(Unit)
    override suspend fun get(key: String): Resource<String?> = Resource.Success(null)
    override suspend fun clear(key: String): Resource<Unit> = Resource.Success(Unit)
}

/** Logs into the shared [log] instead of hitting HTTP — upload/skip logic is [VerseAudioPlanTest]'s
 * and [DefaultVerseAudioUploaderTest]'s job; this test is about the repository's call ordering. */
private class LoggingFakeUploader(private val log: MutableList<String>) : VerseAudioUploader {
    override fun upload(plan: VerseAudioPlan): Flow<UploadProgress> = flow {
        log += "upload"
        emit(UploadProgress.Done)
    }
}

private fun audio(fileRef: String, id: String = "a-$fileRef") =
    DraftAudio(id = id, fileRef = fileRef, durationMs = 1000, sizeBytes = 10, sampleRate = 44100, channels = 1)

private fun draft(verses: List<DraftVerse>, remoteRevision: String? = "1") = MatnDraft(
    id = "m1", title = "T", author = "A", description = "", coverImageRef = null,
    structureKind = StructureKind.SIMPLE, defaultReciterId = "r1", chapters = emptyList(),
    verses = verses, publicationState = PublicationState.DRAFT, createdAt = 0L, updatedAt = 0L,
    remoteRevision = remoteRevision,
)

class SupabaseCatalogRepositoryAudioTest {

    private val config = SupabaseConfig(projectUrl = "https://p.supabase.co", anonKey = "anon-key", bucket = "matn-content")
    private val session = TeacherSession(
        uid = "uid1", displayName = "Teacher", email = "t@example.com",
        accessToken = "access-1", refreshToken = "refresh-1", accessTokenExpiresAt = Long.MAX_VALUE,
    )

    private fun repository(
        log: MutableList<String>,
        writeStatus: HttpStatusCode = HttpStatusCode.OK,
        writeReturnsEmpty: Boolean = false,
        deleteStatus: HttpStatusCode = HttpStatusCode.OK,
        listBody: String = "[]",
        selectBody: String = """[{"id":"m1","revision":5}]""",
        deletedPaths: MutableList<String> = mutableListOf(),
    ): SupabaseCatalogRepository {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path.contains("/object/list/") -> {
                    log += "list"
                    respond(listBody, HttpStatusCode.OK)
                }
                request.method == HttpMethod.Delete -> {
                    log += "delete"
                    deletedPaths += path
                    respond("{}", deleteStatus)
                }
                request.method == HttpMethod.Get && path.contains("/rest/v1/matns") -> {
                    log += "select"
                    respond(selectBody, HttpStatusCode.OK)
                }
                path.contains("/rest/v1/matns") -> {
                    log += "write"
                    val body = when {
                        !writeStatus.isSuccess() -> "server error"
                        writeReturnsEmpty -> "[]"
                        else -> """[{"id":"m1","revision":2}]"""
                    }
                    respond(body, writeStatus)
                }
                else -> respond("{}", HttpStatusCode.OK)
            }
        }
        val httpClient = createHttpClient(engine)
        val refresher = TokenRefresher(SupabaseAuthClient(httpClient, config), FakeSecretStore(), nowMillis = { 0L })
        refresher.setSession(session)
        val postgrest = PostgrestClient(httpClient, config, refresher)
        val storageClient = StorageRestClient(httpClient, config, refresher)
        return SupabaseCatalogRepository(postgrest, storageClient, LoggingFakeUploader(log))
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299

    @Test
    fun `applySplit calls list, then upload, then write, then delete in order`() = runTest {
        val log = mutableListOf<String>()
        val before = draft(listOf(DraftVerse("v1", null, 1, "t", audio("matns/m1/verses/v1-old.mp3"), 1000L)))
        val repo = repository(log)

        val newAudio = audio("matns/m1/verses/v1-new.mp3")
        val payload = PendingUpload("v1", newAudio.fileRef, ByteArray(10), newAudio)

        val result = repo.applySplit(before, mapOf("v1" to newAudio), listOf(payload))

        assertTrue(result is Resource.Success)
        assertEquals(listOf("list", "upload", "write", "delete"), log)
    }

    @Test
    fun `a failed write performs no delete`() = runTest {
        val log = mutableListOf<String>()
        val before = draft(listOf(DraftVerse("v1", null, 1, "t", audio("matns/m1/verses/v1-old.mp3"), 1000L)))
        val repo = repository(log, writeStatus = HttpStatusCode.InternalServerError)

        val newAudio = audio("matns/m1/verses/v1-new.mp3")
        val payload = PendingUpload("v1", newAudio.fileRef, ByteArray(10), newAudio)

        val result = repo.applySplit(before, mapOf("v1" to newAudio), listOf(payload))

        assertTrue(result is Resource.Failure)
        assertTrue("delete" !in log)
        assertEquals(listOf("list", "upload", "write"), log)
    }

    @Test
    fun `a failed delete still returns Resource Success`() = runTest {
        val log = mutableListOf<String>()
        val before = draft(listOf(DraftVerse("v1", null, 1, "t", audio("matns/m1/verses/v1-old.mp3"), 1000L)))
        val repo = repository(log, deleteStatus = HttpStatusCode.InternalServerError)

        val newAudio = audio("matns/m1/verses/v1-new.mp3")
        val payload = PendingUpload("v1", newAudio.fileRef, ByteArray(10), newAudio)

        val result = repo.applySplit(before, mapOf("v1" to newAudio), listOf(payload))

        assertTrue(result is Resource.Success)
        assertEquals(listOf("list", "upload", "write", "delete"), log)
    }

    @Test
    fun `replacing one verse's audio on a published matn leaves other verses' fileRefs byte-identical`() = runTest {
        val log = mutableListOf<String>()
        val v2Audio = audio("matns/m1/verses/v2-untouched.mp3")
        val before = draft(
            listOf(
                DraftVerse("v1", null, 1, "t", audio("matns/m1/verses/v1-old.mp3"), 1000L),
                DraftVerse("v2", null, 2, "t", v2Audio, 1000L),
            ),
        ).copy(publicationState = PublicationState.PUBLISHED)
        val repo = repository(log)

        val newAudio = audio("matns/m1/verses/v1-new.mp3")
        val payload = PendingUpload("v1", newAudio.fileRef, ByteArray(10), newAudio)
        val result = repo.applySplit(before, mapOf("v1" to newAudio), listOf(payload))

        assertTrue(result is Resource.Success)
        assertEquals(v2Audio, result.data.verses.first { it.id == "v2" }.audio)
        assertEquals(PublicationState.PUBLISHED, result.data.publicationState) // FR-034: unchanged
    }

    @Test
    fun `a stale revision yields RemoteError Conflict and performs no delete`() = runTest {
        val log = mutableListOf<String>()
        val before = draft(listOf(DraftVerse("v1", null, 1, "t", audio("matns/m1/verses/v1-old.mp3"), 1000L)))
        // The probe select finds the row, so the ambiguous "0 rows updated" resolves to Conflict,
        // not Forbidden (SupabaseCatalogRepository.diagnoseFailedUpdate).
        val repo = repository(log, writeReturnsEmpty = true, selectBody = """[{"id":"m1","revision":9}]""")

        val newAudio = audio("matns/m1/verses/v1-new.mp3")
        val payload = PendingUpload("v1", newAudio.fileRef, ByteArray(10), newAudio)
        val result = repo.applySplit(before, mapOf("v1" to newAudio), listOf(payload))

        assertEquals(Resource.Failure(com.giraffe.matn.domain.error.RemoteError.Conflict), result)
        assertTrue("delete" !in log)
    }

    @Test
    fun `deleting a verse with audio adds exactly that object to deletes`() = runTest {
        val log = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        val staleRef = "matns/m1/verses/v1-old.mp3"
        val before = draft(
            listOf(
                DraftVerse("v1", null, 1, "t", audio(staleRef), 1000L),
                DraftVerse("v2", null, 2, "t", audio("matns/m1/verses/v2-keep.mp3"), 1000L),
            ),
        )
        val repo = repository(log, deletedPaths = deletedPaths)

        val result = repo.removeVerseAudio(before, "v1")

        assertTrue(result is Resource.Success)
        assertEquals(1, deletedPaths.size)
        assertTrue(deletedPaths.single().endsWith(staleRef))
    }

    @Test
    fun `an unpublish-republish cycle preserves every DraftAudio id and fileRef`() = runTest {
        val log = mutableListOf<String>()
        val v1Audio = audio("matns/m1/verses/v1.mp3", id = "stable-audio-id")
        val published = draft(listOf(DraftVerse("v1", null, 1, "t", v1Audio, 1000L)))
            .copy(publicationState = PublicationState.PUBLISHED)
        val repo = repository(log)

        val unpublished = (repo.publish(published.copy(publicationState = PublicationState.DRAFT)) as Resource.Success).data
        val republished = (repo.publish(unpublished.copy(publicationState = PublicationState.PUBLISHED)) as Resource.Success).data

        assertEquals(v1Audio, republished.verses.single().audio)
    }
}
