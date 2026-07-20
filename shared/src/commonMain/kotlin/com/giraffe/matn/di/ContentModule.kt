package com.giraffe.matn.di

import com.giraffe.matn.data.audio.AudioSourceResolverImpl
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.db.buildDatabase
import com.giraffe.matn.data.repository.AudioAssetRepositoryImpl
import com.giraffe.matn.data.repository.InMemoryRepetitionSettingsStore
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.repository.ReadingPreferencesRepositoryImpl
import com.giraffe.matn.data.repository.VerseRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoader
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.audio.WakeLock
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.MatnRepository
import com.giraffe.matn.domain.repository.ReadingPreferencesRepository
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.repository.VerseRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import com.giraffe.matn.domain.usecase.GetFontSizeUseCase
import com.giraffe.matn.domain.usecase.GetMatnDetailsUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryUseCase
import com.giraffe.matn.domain.usecase.ObserveVersesUseCase
import com.giraffe.matn.domain.usecase.SetFontSizeUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.player.PlayerBarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin module wiring the content domain + data layer (Phase 0). The app's
 * platform module must also register a `single { DatabaseDriverFactory(...) }`
 * provider here before starting Koin, since `DatabaseDriverFactory` is a
 * platform-specific `expect`/`actual` whose construction depends on the host
 * (Android `Context` / iOS bundle).
 */
fun contentModule() = module {
    single { buildDatabase(get<DatabaseDriverFactory>()) }
    single<MatnRepository> { MatnRepositoryImpl(get()) }
    single<VerseRepository> { VerseRepositoryImpl(get()) }
    single<AudioAssetRepository> { AudioAssetRepositoryImpl(get()) }
    single<ContentSeedLoader> { ContentSeedLoaderImpl(get()) }
    factory { GetMatnDetailsUseCase(get()) }
    factory { ObserveVersesUseCase(get()) }
    factory { ObserveLibraryUseCase(get()) }
    single<ReadingPreferencesRepository> { ReadingPreferencesRepositoryImpl(get()) }
    factory { GetFontSizeUseCase(get()) }
    factory { SetFontSizeUseCase(get()) }
    single<AudioSourceResolver> { AudioSourceResolverImpl() }
    factory { BuildPlaybackQueueUseCase(get(), get(), get()) }
    single<RepetitionSettingsStore> { InMemoryRepetitionSettingsStore() }
    single { PlaybackController(get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    factory { PlayerBarViewModel(get()) }
}