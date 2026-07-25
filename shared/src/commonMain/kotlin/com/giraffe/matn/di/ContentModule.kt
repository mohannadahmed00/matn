package com.giraffe.matn.di

import com.giraffe.matn.data.audio.AudioSourceResolverImpl
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.db.buildDatabase
import com.giraffe.matn.data.delivery.ContentPackRepositoryImpl
import com.giraffe.matn.data.repository.AudioAssetRepositoryImpl
import com.giraffe.matn.data.repository.BookmarkRepositoryImpl
import com.giraffe.matn.data.repository.DailyGoalRepositoryImpl
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.repository.NoteRepositoryImpl
import com.giraffe.matn.data.repository.PersistentRepetitionSettingsStore
import com.giraffe.matn.data.repository.ProgressRepositoryImpl
import com.giraffe.matn.data.repository.ReadingPreferencesRepositoryImpl
import com.giraffe.matn.data.repository.SearchRepositoryImpl
import com.giraffe.matn.data.repository.SessionStateRepositoryImpl
import com.giraffe.matn.data.repository.VerseRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoader
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.audio.WakeLock
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.BookmarkRepository
import com.giraffe.matn.domain.repository.ContentPackRepository
import com.giraffe.matn.domain.repository.DailyGoalRepository
import com.giraffe.matn.domain.repository.MatnRepository
import com.giraffe.matn.domain.repository.NoteRepository
import com.giraffe.matn.domain.repository.ProgressRepository
import com.giraffe.matn.domain.repository.ReadingPreferencesRepository
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.repository.SearchRepository
import com.giraffe.matn.domain.repository.SessionStateRepository
import com.giraffe.matn.domain.repository.VerseRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import com.giraffe.matn.domain.usecase.CancelInstallUseCase
import com.giraffe.matn.domain.usecase.DeleteNoteUseCase
import com.giraffe.matn.domain.usecase.DismissContinueLearningUseCase
import com.giraffe.matn.domain.usecase.EnsureMatnPlayableUseCase
import com.giraffe.matn.domain.usecase.GetFontSizeUseCase
import com.giraffe.matn.domain.usecase.GetMatnDetailsUseCase
import com.giraffe.matn.domain.usecase.GetNoteUseCase
import com.giraffe.matn.domain.usecase.InstallMatnContentUseCase
import com.giraffe.matn.domain.usecase.MarkChapterMemorizedUseCase
import com.giraffe.matn.domain.usecase.ObserveBookmarksUseCase
import com.giraffe.matn.domain.usecase.ObserveContentAvailabilityUseCase
import com.giraffe.matn.domain.usecase.ObserveContinueLearningUseCase
import com.giraffe.matn.domain.usecase.ObserveDailyProgressUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryAvailabilityUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryProgressUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryUseCase
import com.giraffe.matn.domain.usecase.ObserveMatnProgressUseCase
import com.giraffe.matn.domain.usecase.ObserveNotesUseCase
import com.giraffe.matn.domain.usecase.ObserveVerseAnnotationsUseCase
import com.giraffe.matn.domain.usecase.ObserveVerseMemorizationUseCase
import com.giraffe.matn.domain.usecase.ObserveVersesUseCase
import com.giraffe.matn.domain.usecase.RemoveAllContentUseCase
import com.giraffe.matn.domain.usecase.RemoveMatnContentUseCase
import com.giraffe.matn.domain.usecase.ResolveResumeTargetUseCase
import com.giraffe.matn.domain.usecase.SaveNoteUseCase
import com.giraffe.matn.domain.usecase.SearchLibraryUseCase
import com.giraffe.matn.domain.usecase.SetDailyGoalUseCase
import com.giraffe.matn.domain.usecase.SetFontSizeUseCase
import com.giraffe.matn.domain.usecase.ToggleBookmarkUseCase
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.playback.PracticeSignalRecorder
import com.giraffe.matn.playback.SessionStateRecorder
import com.giraffe.matn.presentation.player.PlayerBarViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.dsl.module

/**
 * Koin module wiring the content domain + data layer (Phase 0). The app's
 * platform module must also register a `single { DatabaseDriverFactory(...) }`
 * provider here before starting Koin, since `DatabaseDriverFactory` is a
 * platform-specific `expect`/`actual` whose construction depends on the host
 * (Android `Context` / iOS bundle).
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun contentModule() = module {
    single { buildDatabase(get<DatabaseDriverFactory>()) }
    single<MatnRepository> { MatnRepositoryImpl(get()) }
    single<VerseRepository> { VerseRepositoryImpl(get()) }
    single<AudioAssetRepository> { AudioAssetRepositoryImpl(get()) }
    single<ContentSeedLoader> { ContentSeedLoaderImpl(get()) }
    factory { GetMatnDetailsUseCase(get(), get()) }
    factory { ObserveVersesUseCase(get()) }
    factory { ObserveLibraryUseCase(get()) }
    single<ReadingPreferencesRepository> { ReadingPreferencesRepositoryImpl(get()) }
    factory { GetFontSizeUseCase(get()) }
    factory { SetFontSizeUseCase(get()) }
    single<AudioSourceResolver> { AudioSourceResolverImpl(get()) }
    factory { BuildPlaybackQueueUseCase(get(), get(), get()) }
    factory { ObserveContinueLearningUseCase(get()) }
    factory { ResolveResumeTargetUseCase(get(), get()) }
    factory { DismissContinueLearningUseCase(get()) }
    single<SessionStateRepository> { SessionStateRepositoryImpl(get()) }
    single<RepetitionSettingsStore> { PersistentRepetitionSettingsStore(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    // Phase 8 (FR-011/FR-012, research D9): registered ahead of PlaybackController below, which
    // consults it once per session start. Resolved by explicit type below since
    // PlaybackController's parameter is typed as the UseCase<String, Unit> interface, not this
    // concrete class.
    factory { EnsureMatnPlayableUseCase(get()) }
    single {
        PlaybackController(
            engine = get(),
            buildQueue = get(),
            wakeLock = get(),
            settingsStore = get(),
            ensureMatnPlayable = get<EnsureMatnPlayableUseCase>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
    // T040: the recorder observes the SAME PlaybackController singleton the UI uses. Started once
    // in initMatnKoin; platform lifecycle hooks reach it via flushSessionState() (T039).
    single { SessionStateRecorder(get<PlaybackController>().state, get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    factory { PlayerBarViewModel(get()) }
    single<SearchRepository> { SearchRepositoryImpl(get()) }
    factory { SearchLibraryUseCase(get()) }
    single<BookmarkRepository> {
        BookmarkRepositoryImpl(get(), clock = { Clock.System.now().toEpochMilliseconds() }, newId = { Uuid.random().toString() })
    }
    factory { ToggleBookmarkUseCase(get()) }
    factory { ObserveBookmarksUseCase(get()) }
    single<NoteRepository> {
        NoteRepositoryImpl(get(), clock = { Clock.System.now().toEpochMilliseconds() }, newId = { Uuid.random().toString() })
    }
    factory { SaveNoteUseCase(get()) }
    factory { DeleteNoteUseCase(get()) }
    factory { GetNoteUseCase(get()) }
    factory { ObserveNotesUseCase(get()) }
    factory { ObserveVerseAnnotationsUseCase(get(), get()) }
    single<ProgressRepository> {
        ProgressRepositoryImpl(
            get(),
            // `todayIn` did not resolve consistently across every target against this file's
            // kotlin.time.Clock (kotlinx-datetime cross-target version skew); toLocalDateTime
            // derives the same local epoch-day uniformly (research D1).
            today = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays() },
            clock = { Clock.System.now().toEpochMilliseconds() },
            newId = { Uuid.random().toString() },
        )
    }
    factory { ToggleVerseMemorizedUseCase(get()) }
    factory { MarkChapterMemorizedUseCase(get()) }
    factory { ObserveVerseMemorizationUseCase(get()) }
    factory { ObserveMatnProgressUseCase(get()) }
    factory { ObserveLibraryProgressUseCase(get()) }
    single<DailyGoalRepository> { DailyGoalRepositoryImpl(get()) }
    factory { SetDailyGoalUseCase(get()) }
    factory { ObserveDailyProgressUseCase(get(), get()) }
    // T038: mirrors the SessionStateRecorder registration above — observes the SAME
    // PlaybackController singleton; started once in initMatnKoin.
    single { PracticeSignalRecorder(get<PlaybackController>().state, get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }

    // Phase 8 (FR-001): content delivery repository above the platform engine + DeviceStorage seam.
    single<ContentPackRepository> { ContentPackRepositoryImpl(get(), get(), get()) }
    // Phase 8 (US1 T052): install/observe use cases. EnsureMatnPlayableUseCase is registered
    // above, ahead of PlaybackController, which depends on it.
    factory { ObserveContentAvailabilityUseCase(get()) }
    factory { ObserveLibraryAvailabilityUseCase(get()) }
    factory { InstallMatnContentUseCase(get(), get()) }
    factory { CancelInstallUseCase(get()) }
    // Phase 8 (US2 T061): removal use cases.
    factory { RemoveMatnContentUseCase(get(), get()) }
    factory { RemoveAllContentUseCase(get()) }
}