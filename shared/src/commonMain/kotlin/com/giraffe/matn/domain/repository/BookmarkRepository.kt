package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.BookmarkEntry
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    /**
     * Atomic toggle (annotations-contract.md § Repository interfaces): bookmarked -> removed,
     * unbookmarked -> created (fresh UUID, clock timestamp). Returns the resulting state.
     * Serialized per verse — rapid toggling settles to a consistent single-row/no-row state.
     */
    suspend fun toggle(verseId: String): Resource<Boolean>

    /** Newest-first (`created_at` DESC, `id` DESC tiebreak) — the Notes tab's bookmarks section. */
    fun observeAll(): Flow<List<BookmarkEntry>>

    /** Per-matn bookmarked verse ids — feeds the reading carousel's indicator map. */
    fun observeBookmarkedVerseIds(matnId: String): Flow<Set<String>>
}
