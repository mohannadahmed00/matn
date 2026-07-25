package com.giraffe.matn.data

import com.giraffe.matn.data.repository.AppearancePreferencesRepositoryImpl
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T035: `AppearancePreferencesRepository` over the existing `app_setting` table (no migration).
 *  - default when absent (SYSTEM);
 *  - round-trip persistence of each mode;
 *  - `themeModeNow()` agrees with the flow's first emission (FR-005 / research D12).
 */
class AppearancePreferencesRepositoryTest {

    @Test
    fun `default is SYSTEM when unset`() = runTest {
        val repo = AppearancePreferencesRepositoryImpl(newTestDatabase())
        assertEquals(ThemeMode.SYSTEM, repo.themeModeNow())
        assertEquals(ThemeMode.SYSTEM, repo.observeThemeMode().first())
    }

    @Test
    fun `setThemeMode then observe emits the new mode`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        val repo = AppearancePreferencesRepositoryImpl(db)
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.observeThemeMode().first())
        assertEquals(ThemeMode.DARK, repo.themeModeNow())
    }

    @Test
    fun `fresh repository over the same DB reads the persisted mode`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        AppearancePreferencesRepositoryImpl(db).setThemeMode(ThemeMode.LIGHT)
        val freshRepo = AppearancePreferencesRepositoryImpl(db)
        assertEquals(ThemeMode.LIGHT, freshRepo.themeModeNow())
        assertEquals(ThemeMode.LIGHT, freshRepo.observeThemeMode().first())
    }

    @Test
    fun `themeModeNow agrees with the flow's first emission in every stored state`() = runTest {
        for (mode in ThemeMode.entries) {
            val db: ContentDatabase = newTestDatabase()
            AppearancePreferencesRepositoryImpl(db).setThemeMode(mode)
            val repo = AppearancePreferencesRepositoryImpl(db)
            assertEquals(repo.themeModeNow(), repo.observeThemeMode().first())
        }
    }

    @Test
    fun `unrecognized stored value falls back to SYSTEM`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        db.contentQueries.upsertSetting("theme_mode", "BOGUS_MODE")
        val repo = AppearancePreferencesRepositoryImpl(db)
        assertEquals(ThemeMode.SYSTEM, repo.observeThemeMode().first())
        assertEquals(ThemeMode.SYSTEM, repo.themeModeNow())
    }
}