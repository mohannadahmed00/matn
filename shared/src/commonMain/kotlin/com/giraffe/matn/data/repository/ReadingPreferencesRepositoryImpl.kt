package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.repository.ReadingPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_READING_FONT_SIZE = "reading_font_size"

/**
 * Implements [ReadingPreferencesRepository] over the additive `app_setting` key/value table
 * (data-model §2.2). Reads via `selectSetting` as a cold [Flow] of the single stored row or
 * null; writes via `upsertSetting` behind the shared [storageCall] helper so failures surface
 * as [com.giraffe.matn.core.AppError.Storage] rather than throwing.
 *
 * An absent or unrecognized stored value falls back to [ReadingFontSize.DEFAULT] (MEDIUM) —
 * the preference never crashes the UI (FR-016/SC-007).
 */
@org.koin.core.annotation.Single(binds = [ReadingPreferencesRepository::class])
class ReadingPreferencesRepositoryImpl(
    private val db: ContentDatabase,
) : ReadingPreferencesRepository {

    override fun observeFontSize(): Flow<ReadingFontSize> =
        db.contentQueries
            .selectSetting(KEY_READING_FONT_SIZE)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { value -> ReadingFontSize.fromStorageOrDefault(value) }

    override suspend fun setFontSize(size: ReadingFontSize): Resource<Unit> =
        storageCall({ "Failed to persist reading font size" }) {
            db.contentQueries.upsertSetting(KEY_READING_FONT_SIZE, size.name)
        }
}