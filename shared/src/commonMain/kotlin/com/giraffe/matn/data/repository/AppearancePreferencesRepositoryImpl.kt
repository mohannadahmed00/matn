package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.repository.AppearancePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_THEME_MODE = "theme_mode"

/**
 * Implements [AppearancePreferencesRepository] over the additive `app_setting` key/value table
 * (data-model §1) — copy of [ReadingPreferencesRepositoryImpl] with the key, type, and accessor
 * changed. No new table, no migration (research D2).
 *
 *  * [observeThemeMode] is a cold [Flow] over `selectSetting`; an absent or unrecognized value
 *    falls back to [ThemeMode.DEFAULT] (SYSTEM) — never throws.
 *  * [themeModeNow] is a synchronous read used as the StateFlow's initial value so the first
 *    composed frame already carries the student's choice (FR-005, research D12).
 *  * [setThemeMode] persists via `upsertSetting` behind [storageCall]. The resolved appearance is
 *    mirrored to the platform launch-window store by the `AppViewModel` (it needs the system-dark
 *    flag, which lives in the presentation layer — see T041).
 */
class AppearancePreferencesRepositoryImpl(
    private val db: ContentDatabase,
) : AppearancePreferencesRepository {

    override fun observeThemeMode(): Flow<ThemeMode> =
        db.contentQueries
            .selectSetting(KEY_THEME_MODE)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { value -> ThemeMode.fromStorageOrDefault(value) }

    override fun themeModeNow(): ThemeMode =
        ThemeMode.fromStorageOrDefault(
            db.contentQueries.selectSetting(KEY_THEME_MODE).executeAsOneOrNull(),
        )

    override suspend fun setThemeMode(mode: ThemeMode): Resource<Unit> =
        storageCall({ "Failed to persist theme mode" }) {
            db.contentQueries.upsertSetting(KEY_THEME_MODE, mode.name)
        }
}