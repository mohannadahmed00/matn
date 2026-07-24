package com.giraffe.matn.data.repository

import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.model.decodeRepeatCount
import com.giraffe.matn.domain.model.encode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase 4 persistent [RepetitionSettingsStore] — the seam Phase 3 promised would substitute in
 * "without touching a single caller". The interface is unchanged: [get] stays synchronous and
 * answers from an in-memory cache; [put] updates the cache synchronously and persists through the
 * supplied [scope] so callers never block on I/O.
 *
 * Settings are stored on the `matn_session` row for each matn (data-model §1.1), keyed by `matn_id`.
 * Because the row's other columns (last_verse_id, position_ms, ...) are NOT known here, a `put`
 * upserts with sensible non-null placeholders when no session row exists yet — so a configured-but-
 * unplayed drill still survives a restart (FR-002b). The next real session write overwrites those
 * placeholders with real values.
 *
 * No method throws: [get] on an unknown matn returns [RepetitionSettings] defaults (S1, FR-024);
 * a [put] with no prior row creates one (S5, FR-002b); per-matn rows never bleed (S3, SC-009).
 */
class PersistentRepetitionSettingsStore(
    private val db: ContentDatabase,
    private val scope: CoroutineScope,
) : RepetitionSettingsStore {

    private val mutex = Mutex()

    // Main-thread confined (no `synchronized` — it doesn't exist on Kotlin/Native, and rule 6
    // forbids an expect/actual lock): every reader/writer of this map — PlaybackController,
    // ViewModels, warmCache — runs on the main dispatcher. The background [scope] only touches
    // the database, never this map.
    private val cache = mutableMapOf<String, RepetitionSettings>()

    /** Synchronous — answers from the cache, loading the persisted row on first touch (T036).
     *  Without the load-on-miss, opening a matn directly in a *later app run* would hit a cold
     *  cache, hand `startSession` defaults, and the follow-up `put` would overwrite the saved
     *  drill (FR-023). The read is a single indexed row — cheap enough to stay synchronous. */
    override fun get(matnId: String): RepetitionSettings =
        cache.getOrPut(matnId) { readSettings(matnId) ?: RepetitionSettings() }

    /** Synchronously updates the cache, then persists through [scope]. Never throws to the caller. */
    override fun put(matnId: String, settings: RepetitionSettings) {
        cache[matnId] = settings
        scope.launch {
            persist(matnId, settings)
        }
    }

    /** Loads a matn's previously-persisted settings into the cache if present. Called on the resume
     *  path before [com.giraffe.matn.playback.PlaybackController.startSession] reads the store, so a
     *  cold cache after an app restart yields the saved drill rather than defaults (T035). */
    suspend fun warmCache(matnId: String) {
        val settings = mutex.withLock { readSettings(matnId) }
        if (settings != null) cache[matnId] = settings
    }

    private suspend fun persist(matnId: String, settings: RepetitionSettings) {
        val existing = mutex.withLock { readRow(matnId) }
        if (existing == null) {
            // No session row yet — create one with placeholder verse/position values. The counters
            // and loop range are real; the resume anchor defaults until playback writes a real one.
            db.contentQueries.upsertSession(
                matn_id = matnId,
                last_verse_id = "",
                last_verse_display_number = 0L,
                position_ms = 0L,
                verse_repeat = settings.verseRepeat.encode(),
                matn_repeat = settings.matnRepeat.encode(),
                loop_start_verse_id = settings.loopRange?.startVerseId,
                loop_end_verse_id = settings.loopRange?.endVerseId,
            )
        } else {
            // Preserve every other column; rewrite only the settings trio.
            db.contentQueries.upsertSession(
                matn_id = matnId,
                last_verse_id = existing.last_verse_id,
                last_verse_display_number = existing.last_verse_display_number,
                position_ms = existing.position_ms,
                verse_repeat = settings.verseRepeat.encode(),
                matn_repeat = settings.matnRepeat.encode(),
                loop_start_verse_id = settings.loopRange?.startVerseId,
                loop_end_verse_id = settings.loopRange?.endVerseId,
            )
        }
    }

    private fun readRow(matnId: String) =
        try { db.contentQueries.selectSession(matnId).executeAsOneOrNull() } catch (t: Throwable) { null }

    private fun readSettings(matnId: String): RepetitionSettings? {
        val row = readRow(matnId) ?: return null
        val loop = if (row.loop_start_verse_id != null && row.loop_end_verse_id != null) {
            LoopRange(row.loop_start_verse_id, row.loop_end_verse_id)
        } else null
        return RepetitionSettings(
            verseRepeat = decodeRepeatCount(row.verse_repeat),
            matnRepeat = decodeRepeatCount(row.matn_repeat),
            loopRange = loop,
        )
    }
}