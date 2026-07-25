package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Phase 9 appearance preference (FR-006, contract theming §1).
 *
 *  * [observeThemeMode] emits the persisted mode as a cold [Flow]; an absent or unrecognized
 *    stored value resolves to [ThemeMode.DEFAULT] (SYSTEM) — never throws (data-model §2.1).
 *  * [themeModeNow] is a synchronous read used as the StateFlow's initial value so the first
 *    composed frame already carries the student's choice (FR-005, research D12).
 *  * [setThemeMode] persists immediately (Principle VI) and mirrors the resolved appearance
 *    to the platform launch-window store (research D9).
 */
interface AppearancePreferencesRepository {
    /** Live mode; an absent or unrecognized stored value resolves to [ThemeMode.SYSTEM]. */
    fun observeThemeMode(): Flow<ThemeMode>

    /**
     * Synchronous read of the stored mode, used as the StateFlow's initial value so the first
     * composed frame already carries the student's choice (FR-005, research D12).
     */
    fun themeModeNow(): ThemeMode

    /** Persists immediately (Principle VI) and mirrors the resolved appearance to the platform store. */
    suspend fun setThemeMode(mode: ThemeMode): Resource<Unit>
}