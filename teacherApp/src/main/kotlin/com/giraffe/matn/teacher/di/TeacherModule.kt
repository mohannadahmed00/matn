package com.giraffe.matn.teacher.di

import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.auth.TokenRefresher
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.audio.DefaultVerseAudioUploader
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.data.repository.SupabaseCatalogRepository
import com.giraffe.matn.data.repository.SupabaseTeacherAuthRepository
import com.giraffe.matn.data.audio.FrameAccurateSlicer
import com.giraffe.matn.domain.audio.AudioProbe
import com.giraffe.matn.domain.audio.AudioSlicer
import com.giraffe.matn.domain.audio.PreviewPlayer
import com.giraffe.matn.domain.audio.VerseAudioUploader
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.secret.SecretStore
import com.giraffe.matn.domain.usecase.ApplySplitUseCase
import com.giraffe.matn.domain.usecase.AttachVerseAudioUseCase
import com.giraffe.matn.domain.usecase.ListAuthoredMatnsUseCase
import com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase
import com.giraffe.matn.domain.usecase.LoadSplitSourceUseCase
import com.giraffe.matn.domain.usecase.PreviewMatnAudioUseCase
import com.giraffe.matn.domain.usecase.PublishMatnUseCase
import com.giraffe.matn.domain.usecase.RemoveVerseAudioUseCase
import com.giraffe.matn.domain.usecase.RestoreSessionUseCase
import com.giraffe.matn.domain.usecase.SaveDraftUseCase
import com.giraffe.matn.domain.usecase.SignInUseCase
import com.giraffe.matn.domain.usecase.SignOutUseCase
import com.giraffe.matn.domain.usecase.UnpublishMatnUseCase
import com.giraffe.matn.domain.usecase.LoadCoverImageUseCase
import com.giraffe.matn.domain.usecase.UploadCoverImageUseCase
import com.giraffe.matn.domain.usecase.ValidateMatnUseCase
import com.giraffe.matn.teacher.platform.JLayerAudioProbe
import com.giraffe.matn.teacher.platform.JvmPreviewPlayer
import com.giraffe.matn.teacher.platform.PreviewCache
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication
import java.io.File
import java.util.Properties
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Explicit provider functions for every teacher-side dependency, per Ground Rule 11: teacher-side
 * classes added to `:shared` (`SupabaseConfig`'s consumers, the REST clients, repositories, use
 * cases) MUST NOT carry Koin annotations there — `:shared`'s `ContentModule` `@ComponentScan`
 * would otherwise pull them into the student apps' graph, which has no `SupabaseConfig`/
 * `HttpClient`/`SecretStore` to give them. `@ComponentScan` here is scoped to
 * `com.giraffe.matn.teacher` only, so it is safe to scan `:teacherApp`'s own ViewModels/platform
 * classes. Later tasks add their provider function here as they create each class.
 */
@OptIn(ExperimentalTime::class)
@Module
@ComponentScan("com.giraffe.matn.teacher")
class TeacherModule {

    /** The whole backend: auth, the `matns` table, and binary object storage all live in one
     * Supabase project. Pointing `SUPABASE_URL` at `http://127.0.0.1:54321` runs the tool against
     * a local stack (`supabase start`). */
    @Single
    fun supabaseConfig(): SupabaseConfig {
        val props = teacherLocalProperties()
        return SupabaseConfig(
            projectUrl = System.getenv("SUPABASE_URL") ?: props.getProperty("supabaseUrl", ""),
            anonKey = System.getenv("SUPABASE_ANON_KEY") ?: props.getProperty("supabaseAnonKey", ""),
            bucket = System.getenv("SUPABASE_BUCKET") ?: props.getProperty("supabaseBucket", ""),
        )
    }

    /**
     * Walks up from the working directory looking for `supabase/supabase.local.properties`, because
     * the working directory differs between `./gradlew :teacherApp:run` (repo root) and an IDE run
     * configuration (the module directory). A plain relative path silently produced an empty config,
     * which surfaced as `RemoteError.Network` — a misleading "cannot reach the server" rather than
     * "there is no server configured". Missing entirely is still legal: the environment variables in
     * [supabaseConfig] override the file anyway.
     */
    private fun teacherLocalProperties(): Properties {
        val props = Properties()
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, LOCAL_PROPERTIES_PATH) }
            .firstOrNull { it.isFile }
        file?.inputStream()?.use { props.load(it) }
        return props
    }

    @Single
    fun httpClient(): HttpClient = createHttpClient()

    @Single
    fun supabaseAuthClient(httpClient: HttpClient, supabaseConfig: SupabaseConfig): SupabaseAuthClient =
        SupabaseAuthClient(httpClient, supabaseConfig)

    @Single
    fun tokenRefresher(client: SupabaseAuthClient, secretStore: SecretStore): TokenRefresher =
        TokenRefresher(client, secretStore, nowMillis = { Clock.System.now().toEpochMilliseconds() })

    @Single
    fun teacherAuthRepository(
        client: SupabaseAuthClient,
        tokenRefresher: TokenRefresher,
        secretStore: SecretStore,
    ): TeacherAuthRepository = SupabaseTeacherAuthRepository(client, tokenRefresher, secretStore)

    @Single
    fun signInUseCase(repository: TeacherAuthRepository): SignInUseCase = SignInUseCase(repository)

    @Single
    fun signOutUseCase(repository: TeacherAuthRepository): SignOutUseCase = SignOutUseCase(repository)

    @Single
    fun restoreSessionUseCase(repository: TeacherAuthRepository): RestoreSessionUseCase =
        RestoreSessionUseCase(repository)

    @Single
    fun postgrestClient(httpClient: HttpClient, supabaseConfig: SupabaseConfig, tokenRefresher: TokenRefresher): PostgrestClient =
        PostgrestClient(httpClient, supabaseConfig, tokenRefresher)

    @Single
    fun storageRestClient(httpClient: HttpClient, supabaseConfig: SupabaseConfig, tokenRefresher: TokenRefresher): StorageRestClient =
        StorageRestClient(httpClient, supabaseConfig, tokenRefresher)

    @Single
    fun verseAudioUploader(storageClient: StorageRestClient): VerseAudioUploader =
        DefaultVerseAudioUploader(storageClient)

    @Single
    fun catalogRepository(postgrest: PostgrestClient, storageClient: StorageRestClient, uploader: VerseAudioUploader): CatalogRepository =
        SupabaseCatalogRepository(postgrest, storageClient, uploader)

    @Single
    fun saveDraftUseCase(repository: CatalogRepository): SaveDraftUseCase = SaveDraftUseCase(repository)

    @Single
    fun loadMatnForEditUseCase(repository: CatalogRepository): LoadMatnForEditUseCase = LoadMatnForEditUseCase(repository)

    @Single
    fun uploadCoverImageUseCase(repository: CatalogRepository): UploadCoverImageUseCase = UploadCoverImageUseCase(repository)

    @Single
    fun loadCoverImageUseCase(repository: CatalogRepository): LoadCoverImageUseCase = LoadCoverImageUseCase(repository)

    @Single
    fun validateMatnUseCase(): ValidateMatnUseCase = ValidateMatnUseCase()

    @Single
    fun publishMatnUseCase(repository: CatalogRepository): PublishMatnUseCase = PublishMatnUseCase(repository)

    @Single
    fun listAuthoredMatnsUseCase(repository: CatalogRepository): ListAuthoredMatnsUseCase = ListAuthoredMatnsUseCase(repository)

    @Single
    fun unpublishMatnUseCase(repository: CatalogRepository): UnpublishMatnUseCase = UnpublishMatnUseCase(repository)

    @Single
    fun audioProbe(): AudioProbe = JLayerAudioProbe()

    @Single
    fun attachVerseAudioUseCase(repository: CatalogRepository, audioProbe: AudioProbe): AttachVerseAudioUseCase =
        AttachVerseAudioUseCase(repository, audioProbe)

    @Single
    fun removeVerseAudioUseCase(repository: CatalogRepository): RemoveVerseAudioUseCase = RemoveVerseAudioUseCase(repository)

    @Single
    fun previewCache(storageClient: StorageRestClient): PreviewCache = PreviewCache(storageClient)

    @Single
    fun previewPlayer(previewCache: PreviewCache): PreviewPlayer = JvmPreviewPlayer(previewCache)

    @Single
    fun previewMatnAudioUseCase(previewPlayer: PreviewPlayer): PreviewMatnAudioUseCase = PreviewMatnAudioUseCase(previewPlayer)

    @Single
    fun audioSlicer(): AudioSlicer = FrameAccurateSlicer()

    @Single
    fun loadSplitSourceUseCase(audioProbe: AudioProbe): LoadSplitSourceUseCase = LoadSplitSourceUseCase(audioProbe)

    @Single
    fun applySplitUseCase(repository: CatalogRepository, slicer: AudioSlicer): ApplySplitUseCase = ApplySplitUseCase(repository, slicer)

    private companion object {
        const val LOCAL_PROPERTIES_PATH = "supabase/supabase.local.properties"
    }
}

/** Starts a Koin instance scoped to `:teacherApp` with only [TeacherModule] — never `:shared`'s
 * `ContentModule`, and never `initMatnKoin`, which requires student-only platform singletons and
 * seeds bundled matns. `:teacherApp` opens no SQLDelight database. */
fun startTeacherKoin(): Koin = koinApplication { modules(TeacherModule().module()) }.koin
