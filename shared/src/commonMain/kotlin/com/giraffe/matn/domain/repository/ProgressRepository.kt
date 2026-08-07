package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.MemorizedEntry
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    /**
     * `true` -> upsert a `memorization` row and credit today's practice for [verseId] (D3).
     * `false` -> delete the `memorization` row only; `daily_practice` is untouched (FR-014).
     * Idempotent: setting to the current state is a no-op that still succeeds.
     */
    suspend fun setVerseMemorized(verseId: String, memorized: Boolean): Resource<Unit>

    /**
     * Applies [setVerseMemorized] semantics to every verse of the chapter in one logical
     * operation. `true` credits each newly memorized verse once — already-memorized verses are
     * not re-credited (FR-003).
     */
    suspend fun setChapterMemorized(chapterId: String, memorized: Boolean): Resource<Unit>

    /** Reactive set of memorized verse ids for the matn; re-emits on any change. */
    fun observeMemorizedVerseIds(matnId: String): Flow<Set<String>>

    /**
     * Reactive library-wide memorized list with verse/matn context, newest first — the *Memorized*
     * segment of the Saved tab. Mirrors [BookmarkRepository.observeAll]; rows whose matn has been
     * removed are omitted (they still count toward progress, but have no text to show).
     */
    fun observeMemorized(): Flow<List<MemorizedEntry>>

    /** Reactive `(memorizedCount, totalCount)` for one matn from the single aggregate (D4). */
    fun observeMatnProgress(matnId: String): Flow<MatnProgress>

    /** Reactive progress for all متون in a deterministic (library) order — same source as above. */
    fun observeLibraryProgress(): Flow<List<MatnProgress>>

    /** `INSERT OR IGNORE` a `daily_practice` row for today(); a repeat call is a silent success. */
    suspend fun recordPractice(verseId: String): Resource<Unit>

    /**
     * Reactive count of today's distinct practiced verses. `today()` is re-evaluated on a poll
     * interval and drives the query via `flatMapLatest` (research D9) — a collector held open
     * across local midnight re-emits `0` for the new day without re-subscription (FR-013).
     */
    fun observeTodayPracticeCount(): Flow<Int>
}
