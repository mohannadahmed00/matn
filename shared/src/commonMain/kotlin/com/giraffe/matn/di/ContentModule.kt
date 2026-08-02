package com.giraffe.matn.di

import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.db.buildDatabase
import com.giraffe.matn.data.delivery.ContentFileStore
import com.giraffe.matn.data.delivery.DownloadedContentRepositoryImpl
import com.giraffe.matn.data.delivery.RemoteContentDeliveryEngine
import com.giraffe.matn.data.catalog.StudentCatalogRepositoryImpl
import com.giraffe.matn.data.cover.CoverImageCache
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.AccessTokenProvider
import com.giraffe.matn.data.remote.auth.AnonymousAccessTokenProvider
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.catalog.StudentCatalogRepository
import io.ktor.client.HttpClient
import com.giraffe.matn.data.repository.BookmarkRepositoryImpl
import com.giraffe.matn.data.repository.NoteRepositoryImpl
import com.giraffe.matn.data.repository.PersistentRepetitionSettingsStore
import com.giraffe.matn.data.repository.ProgressRepositoryImpl
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.WakeLock
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.repository.DownloadedContentRepository
import com.giraffe.matn.domain.repository.BookmarkRepository
import com.giraffe.matn.domain.repository.NoteRepository
import com.giraffe.matn.domain.repository.ProgressRepository
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.repository.SessionStateRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import com.giraffe.matn.domain.usecase.EnsureMatnPlayableUseCase
import com.giraffe.matn.domain.usecase.EnsureNotificationPermissionUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.playback.PracticeSignalRecorder
import com.giraffe.matn.playback.SessionStateRecorder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * `@ComponentScan` picks up every direct `@Single`/`@Factory` class under `com.giraffe.matn`
 * (use cases, most repository impls — each annotated in its own file). The functions below
 * cover what a scan can't: classes wired with a runtime lambda/`CoroutineScope` (not a plain
 * constructor dependency Koin can autowire) or built via a factory function rather than a
 * constructor. Both live in one `@Module` class — splitting them into separate `@Module`
 * classes made the compiler plugin report real cross-bindings (e.g. `playbackController`'s
 * `BuildPlaybackQueueUseCase` param, found only via the scan) as hard `KOIN-D001` errors
 * instead of resolving them, even though everything compiles in the same Gradle module.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
@Module
@ComponentScan("com.giraffe.matn")
class ContentModule {

    @Single
    fun contentDatabase(driverFactory: DatabaseDriverFactory): ContentDatabase = buildDatabase(driverFactory)

    // ---------------------------------------------------------------- Phase 13 content delivery

    /**
     * The student's backend access, all of it anonymous (FR-027). One shared [HttpClient]; no
     * logging plugin, so nothing can leak a credential into a log — even though there is no
     * credential to leak on this side.
     */
    @Single
    fun httpClient(): HttpClient = createHttpClient()

    /**
     * There is no student token, and that is the design (student-read-contract §1.1). Binding the
     * anonymous provider here is what lets `PostgrestClient`/`StorageRestClient` be reused
     * unchanged by both the student clients and `:teacherApp`, which binds `TokenRefresher` to the
     * same interface instead.
     */
    @Single
    fun accessTokenProvider(): AccessTokenProvider = AnonymousAccessTokenProvider()

    @Single
    fun postgrestClient(
        client: HttpClient,
        config: SupabaseConfig,
        tokenProvider: AccessTokenProvider,
    ): PostgrestClient = PostgrestClient(client, config, tokenProvider)

    @Single
    fun storageRestClient(
        client: HttpClient,
        config: SupabaseConfig,
        tokenProvider: AccessTokenProvider,
    ): StorageRestClient = StorageRestClient(client, config, tokenProvider)

    @Single
    fun studentCatalogRepository(
        db: ContentDatabase,
        postgrest: PostgrestClient,
    ): StudentCatalogRepository = StudentCatalogRepositoryImpl(
        db = db,
        postgrest = postgrest,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    @Single
    fun contentFileStore(storage: DeviceStorage): ContentFileStore = ContentFileStore(storage)

    /** Browse-time only — never reachable from the reading or playback path (FR-012, SC-004). */
    @Single
    fun coverImageCache(
        storageClient: StorageRestClient,
        files: ContentFileStore,
    ): CoverImageCache = CoverImageCache(storageClient, files)

    /**
     * The one content-acquisition mechanism for all three student clients (FR-041). Replaces the
     * three platform engines, which is why it is built here rather than handed in by the platform
     * shell as `PlatformModule` used to do.
     */
    @Single
    fun contentDeliveryEngine(
        db: ContentDatabase,
        postgrest: PostgrestClient,
        storageClient: StorageRestClient,
        files: ContentFileStore,
        deviceStorage: DeviceStorage,
    ): ContentDeliveryEngine = RemoteContentDeliveryEngine(
        db = db,
        postgrest = postgrest,
        storageClient = storageClient,
        files = files,
        deviceStorage = deviceStorage,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    /**
     * The download queue's owner. Its scope is **application-scoped**, not screen-scoped, so a
     * transfer and the queue behind it keep advancing while the app is backgrounded (FR-018) — the
     * same shape [sessionStateRecorder] and [practiceSignalRecorder] already use.
     */
    @Single
    fun downloadedContentRepository(
        db: ContentDatabase,
        engine: ContentDeliveryEngine,
        storage: DeviceStorage,
        files: ContentFileStore,
    ): DownloadedContentRepository = DownloadedContentRepositoryImpl(
        db = db,
        engine = engine,
        storage = storage,
        files = files,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    @Single
    fun repetitionSettingsStore(db: ContentDatabase): RepetitionSettingsStore =
        PersistentRepetitionSettingsStore(db, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Single
    fun playbackController(
        engine: AudioEngine,
        buildQueue: BuildPlaybackQueueUseCase,
        wakeLock: WakeLock,
        settingsStore: RepetitionSettingsStore,
        ensureMatnPlayable: EnsureMatnPlayableUseCase,
        ensureNotificationPermission: EnsureNotificationPermissionUseCase,
    ): PlaybackController = PlaybackController(
        engine = engine,
        buildQueue = buildQueue,
        wakeLock = wakeLock,
        settingsStore = settingsStore,
        ensureMatnPlayable = ensureMatnPlayable,
        ensureNotificationPermission = ensureNotificationPermission,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    // T040: the recorder observes the SAME PlaybackController singleton the UI uses. Started once
    // in initMatnKoin; platform lifecycle hooks reach it via flushSessionState() (T039).
    @Single
    fun sessionStateRecorder(
        controller: PlaybackController,
        repository: SessionStateRepository,
    ): SessionStateRecorder =
        SessionStateRecorder(controller.state, repository, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    // T038: mirrors the SessionStateRecorder registration above — observes the SAME
    // PlaybackController singleton; started once in initMatnKoin.
    @Single
    fun practiceSignalRecorder(
        controller: PlaybackController,
        repository: ProgressRepository,
    ): PracticeSignalRecorder =
        PracticeSignalRecorder(controller.state, repository, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Single
    fun bookmarkRepository(db: ContentDatabase): BookmarkRepository = BookmarkRepositoryImpl(
        db,
        clock = { Clock.System.now().toEpochMilliseconds() },
        newId = { Uuid.random().toString() },
    )

    @Single
    fun noteRepository(db: ContentDatabase): NoteRepository = NoteRepositoryImpl(
        db,
        clock = { Clock.System.now().toEpochMilliseconds() },
        newId = { Uuid.random().toString() },
    )

    @Single
    fun progressRepository(db: ContentDatabase): ProgressRepository = ProgressRepositoryImpl(
        db,
        // `todayIn` did not resolve consistently across every target against this file's
        // kotlin.time.Clock (kotlinx-datetime cross-target version skew); toLocalDateTime
        // derives the same local epoch-day uniformly (research D1).
        today = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays() },
        clock = { Clock.System.now().toEpochMilliseconds() },
        newId = { Uuid.random().toString() },
    )
}
