package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.NoteEntry
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    /**
     * Create-or-replace (annotations-contract.md § Repository interfaces). Blank/whitespace-only
     * [text] returns `Resource.Failure(NoteError.EmptyNote)` and NEVER persists `""` (FR-019).
     * An existing note keeps its UUID; `updated_at` is refreshed from the injected clock.
     */
    suspend fun save(verseId: String, text: String): Resource<Note>

    /** Explicit delete action only (FR-019) — never triggered by an empty save. */
    suspend fun delete(verseId: String): Resource<Unit>

    /** One-shot read for the editor's prefill. */
    suspend fun get(verseId: String): Resource<Note?>

    /** Newest-first (`updated_at` DESC, `id` DESC tiebreak) — the Notes tab's notes section. */
    fun observeAll(): Flow<List<NoteEntry>>

    /** Per-matn noted verse ids — feeds the reading carousel's indicator map. */
    fun observeNotedVerseIds(matnId: String): Flow<Set<String>>
}
