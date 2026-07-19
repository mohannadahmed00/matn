package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.ReadingFontSize
import kotlinx.coroutines.flow.Flow

/**
 * Phase 1-owned global reading display preference (FR-016/FR-017).
 *
 *  * [observeFontSize] emits the persisted size as a cold [Flow]; an absent or unrecognized
 *    stored value resolves to [ReadingFontSize.DEFAULT] (MEDIUM) — never throws (data-model §2.2).
 *  * [setFontSize] persists immediately on every change (Constitution Principle VI) so the
 *    choice survives app restart (SC-007); observed live so re-renders are immediate (FR-016).
 */
interface ReadingPreferencesRepository {
    fun observeFontSize(): Flow<ReadingFontSize>
    suspend fun setFontSize(size: ReadingFontSize): Resource<Unit>
}