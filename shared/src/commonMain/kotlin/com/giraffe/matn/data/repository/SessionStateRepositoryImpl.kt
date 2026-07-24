package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.db.Matn_session
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.model.SavedMatnSession
import com.giraffe.matn.domain.model.decodeRepeatCount
import com.giraffe.matn.domain.model.encode
import com.giraffe.matn.domain.repository.SessionStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val KEY_LAST_LISTENED_MATN_ID = "last_listened_matn_id"

/**
 * Implements [SessionStateRepository] over the `matn_session` table and the `last_listened_matn_id`
 * `app_setting` row (data-model §1). Mirrors [ReadingPreferencesRepositoryImpl]'s style: cold reads
 * via `asFlow().mapToOneOrNull`, writes behind the shared [storageCall] helper.
 *
 * - `getSession` rebuilds [RepetitionSettings] via [decodeRepeatCount] (total, never throws) and a
 *   `LoopRange` only when both endpoints are non-null. Unreadable rows degrade to `null` (P1).
 * - `observeContinueLearning` observes the pointer, then resolves to the session + matn title,
 *   emitting `null` when the pointer is absent, the session row is missing, or the matn no longer
 *   exists (P4, FR-015, FR-025) — never an error.
 * - `clearLastListenedMatnId` calls `deleteSetting(...)` and nothing else (P2, FR-017a).
 */
class SessionStateRepositoryImpl(
    private val db: ContentDatabase,
) : SessionStateRepository {

    override suspend fun getSession(matnId: String): SavedMatnSession? = withContext(Dispatchers.Default) {
        try {
            db.contentQueries.selectSession(matnId).executeAsOneOrNull()?.toSaved()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            null
        }
    }

    override suspend fun putSession(session: SavedMatnSession): Resource<Unit> =
        storageCall({ "Failed to persist session for ${session.matnId}" }) {
            val loop = session.settings.loopRange
            db.contentQueries.upsertSession(
                matn_id = session.matnId,
                last_verse_id = session.lastVerseId,
                last_verse_display_number = session.lastVerseDisplayNumber.toLong(),
                position_ms = session.positionMs,
                verse_repeat = session.settings.verseRepeat.encode(),
                matn_repeat = session.settings.matnRepeat.encode(),
                loop_start_verse_id = loop?.startVerseId,
                loop_end_verse_id = loop?.endVerseId,
            )
        }

    override suspend fun getLastListenedMatnId(): String? = withContext(Dispatchers.Default) {
        try {
            val matnId = db.contentQueries
                .selectSetting(KEY_LAST_LISTENED_MATN_ID)
                .executeAsOneOrNull()
                ?: return@withContext null
            // A dangling pointer (matn deleted) is treated as unset (FR-025).
            if (db.contentQueries.selectMatnById(matnId).executeAsOneOrNull() == null) null else matnId
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            null
        }
    }

    override suspend fun setLastListenedMatnId(matnId: String): Resource<Unit> =
        storageCall({ "Failed to persist last-listened matn id" }) {
            db.contentQueries.upsertSetting(KEY_LAST_LISTENED_MATN_ID, matnId)
        }

    override suspend fun clearLastListenedMatnId(): Resource<Unit> =
        storageCall({ "Failed to clear last-listened matn id" }) {
            db.contentQueries.deleteSetting(KEY_LAST_LISTENED_MATN_ID)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeContinueLearning(): Flow<ContinueLearningEntry?> =
        db.contentQueries
            .selectSetting(KEY_LAST_LISTENED_MATN_ID)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .flatMapLatest { matnId ->
                if (matnId == null) {
                    flowOf(null)
                } else {
                    combine(
                        db.contentQueries.selectSession(matnId).asFlow().mapToOneOrNull(Dispatchers.Default),
                        db.contentQueries.selectMatnById(matnId).asFlow().mapToOneOrNull(Dispatchers.Default),
                    ) { session, matn ->
                        if (session == null || matn == null) {
                            null
                        } else {
                            ContinueLearningEntry(
                                matnId = matnId,
                                matnTitle = matn.title,
                                verseDisplayNumber = session.last_verse_display_number.toInt(),
                                verseId = session.last_verse_id,
                            )
                        }
                    }
                }
            }
}

/** Row → domain. Decoding is total (decodeRepeatCount never throws); see contracts §1 P1. */
private fun Matn_session.toSaved(): SavedMatnSession {
    val loopRange = if (loop_start_verse_id != null && loop_end_verse_id != null) {
        LoopRange(startVerseId = loop_start_verse_id, endVerseId = loop_end_verse_id)
    } else {
        null
    }
    return SavedMatnSession(
        matnId = matn_id,
        lastVerseId = last_verse_id,
        lastVerseDisplayNumber = last_verse_display_number.toInt(),
        positionMs = position_ms,
        settings = RepetitionSettings(
            verseRepeat = decodeRepeatCount(verse_repeat),
            matnRepeat = decodeRepeatCount(matn_repeat),
            loopRange = loopRange,
        ),
    )
}