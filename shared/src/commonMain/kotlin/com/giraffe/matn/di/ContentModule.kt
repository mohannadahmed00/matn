package com.giraffe.matn.di

import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.db.buildDatabase
import com.giraffe.matn.data.repository.AudioAssetRepositoryImpl
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.repository.VerseRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoader
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.MatnRepository
import com.giraffe.matn.domain.repository.VerseRepository
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
}