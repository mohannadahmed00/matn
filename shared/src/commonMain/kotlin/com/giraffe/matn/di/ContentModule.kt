package com.giraffe.matn.di

import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.db.buildDatabase
import com.giraffe.matn.data.repository.BookmarkRepositoryImpl
import com.giraffe.matn.data.repository.NoteRepositoryImpl
import com.giraffe.matn.data.repository.PersistentRepetitionSettingsStore
import com.giraffe.matn.data.repository.ProgressRepositoryImpl
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.WakeLock
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
