package com.giraffe.matn.audio

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.DefaultVerseAudioUploader
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.auth.TokenRefresher
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.UploadProgress
import com.giraffe.matn.domain.audio.VerseAudioPlan
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.secret.SecretStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeSecretStore : SecretStore {
    override val isProtected: Boolean = true
    override suspend fun put(key: String, value: String): Resource<Unit> = Resource.Success(Unit)
    override suspend fun get(key: String): Resource<String?> = Resource.Success(null)
    override suspend fun clear(key: String): Resource<Unit> = Resource.Success(Unit)
}

private fun payload(verseId: String, objectPath: String, size: Int = 10) = PendingUpload(
    verseId = verseId,
    objectPath = objectPath,
    bytes = ByteArray(size),
    audio = DraftAudio(id = "a-$verseId", fileRef = objectPath, durationMs = 1000, sizeBytes = size.toLong(), sampleRate = 44100, channels = 1),
)

class DefaultVerseAudioUploaderTest {

    /** Fakes only the HTTP boundary via [MockEngine] — [StorageRestClient] and
     * [DefaultVerseAudioUploader] above it run for real (this codebase's `PostgrestClientTest`
     * convention), so cancellation, ordering, skip, and failure are exercised against production
     * code, not a hand-written stand-in. */
    private fun uploaderRecording(
        failObjectPath: String? = null,
    ): Pair<DefaultVerseAudioUploader, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            val path = request.url.encodedPath.substringAfterLast('/')
            if (failObjectPath != null && path == failObjectPath.substringAfterLast('/')) {
                respond("server error", HttpStatusCode.InternalServerError)
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }
        val httpClient = createHttpClient(engine)
        val config = SupabaseConfig(projectUrl = "https://p.supabase.co", anonKey = "anon-key", bucket = "matn-content")
        val refresher = TokenRefresher(SupabaseAuthClient(httpClient, config), FakeSecretStore(), nowMillis = { 0L }).apply {
            setSession(
                TeacherSession(
                    uid = "uid1", displayName = "Teacher", email = "t@example.com",
                    accessToken = "access-1", refreshToken = "refresh-1", accessTokenExpiresAt = Long.MAX_VALUE,
                ),
            )
        }
        val storageClient = StorageRestClient(httpClient, config, refresher)
        return DefaultVerseAudioUploader(storageClient) to requests
    }

    @Test
    fun `progress is emitted per verse`() = runTest {
        val (uploader, requests) = uploaderRecording()
        val plan = VerseAudioPlan(
            uploads = listOf(payload("v1", "matns/m1/verses/ref1.mp3"), payload("v2", "matns/m1/verses/ref2.mp3")),
            skips = emptyList(),
            deletes = emptyList(),
        )

        val events = uploader.upload(plan).toList()

        assertEquals(2, requests.size)
        assertIs<UploadProgress.Done>(events.last())
        assertTrue(events.count { it is UploadProgress.Verse && it.verseId == "v1" } == 2)
    }

    @Test
    fun `skips are not uploaded`() = runTest {
        val (uploader, requests) = uploaderRecording()
        val plan = VerseAudioPlan(
            uploads = listOf(payload("v1", "matns/m1/verses/ref1.mp3")),
            skips = listOf("matns/m1/verses/ref-skipped.mp3"),
            deletes = emptyList(),
        )

        uploader.upload(plan).toList()

        assertEquals(1, requests.size)
        assertTrue(requests.single().url.encodedPath.endsWith("ref1.mp3"))
    }

    @Test
    fun `a mid-batch failure stops the flow and reports the error`() = runTest {
        val (uploader, requests) = uploaderRecording(failObjectPath = "matns/m1/verses/ref2.mp3")
        val plan = VerseAudioPlan(
            uploads = listOf(
                payload("v1", "matns/m1/verses/ref1.mp3"),
                payload("v2", "matns/m1/verses/ref2.mp3"),
                payload("v3", "matns/m1/verses/ref3.mp3"),
            ),
            skips = emptyList(),
            deletes = emptyList(),
        )

        val events = uploader.upload(plan).toList()

        assertEquals(2, requests.size) // ref3 never attempted
        assertIs<UploadProgress.Failed>(events.last())
    }

    @Test
    fun `cancellation stops further uploads`() = runTest {
        val (uploader, requests) = uploaderRecording()
        val plan = VerseAudioPlan(
            uploads = listOf(
                payload("v1", "matns/m1/verses/ref1.mp3"),
                payload("v2", "matns/m1/verses/ref2.mp3"),
                payload("v3", "matns/m1/verses/ref3.mp3"),
            ),
            skips = emptyList(),
            deletes = emptyList(),
        )

        // Taking only the first two events stops the flow after v1's completion, before v2 starts.
        uploader.upload(plan).take(2).toList()

        assertEquals(1, requests.size)
    }
}
