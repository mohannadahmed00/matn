package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

/**
 * SQLDelight-backed [ProgressRepository] (progress-contract.md; research.md D1/D4/D9). [today]
 * (local epoch-day) and [clock] (epoch millis) are injected pure lambdas — production values are
 * supplied only at the Koin wiring site, tests inject fixed/fake ones, mirroring
 * [BookmarkRepositoryImpl]'s constructor shape.
 */
class ProgressRepositoryImpl(
    private val db: ContentDatabase,
    private val today: () -> Long,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val dayCheckIntervalMs: Long = 60_000,
) : ProgressRepository {

    override suspend fun setVerseMemorized(verseId: String, memorized: Boolean): Resource<Unit> =
        storageCall({ "Failed to set memorized state for verse $verseId" }) {
            db.transactionWithResult {
                if (memorized) {
                    val existing = db.contentQueries.selectMemorizationByVerse(verseId).executeAsOneOrNull()
                    if (existing == null) {
                        db.contentQueries.insertMemorization(newId(), verseId, clock())
                        db.contentQueries.insertDailyPractice(newId(), today(), verseId, clock())
                    }
                } else {
                    db.contentQueries.deleteMemorizationByVerse(verseId)
                }
            }
        }

    override suspend fun setChapterMemorized(chapterId: String, memorized: Boolean): Resource<Unit> =
        storageCall({ "Failed to set memorized state for chapter $chapterId" }) {
            db.transactionWithResult {
                val verseIds = db.contentQueries.selectVersesByChapter(chapterId).executeAsList().map { it.id }
                if (memorized) {
                    // One existence check for the whole chapter instead of one per verse (N+1).
                    val alreadyMemorized =
                        db.contentQueries.selectMemorizedVerseIdsByChapter(chapterId).executeAsList().toSet()
                    for (verseId in verseIds) {
                        if (verseId !in alreadyMemorized) {
                            db.contentQueries.insertMemorization(newId(), verseId, clock())
                            db.contentQueries.insertDailyPractice(newId(), today(), verseId, clock())
                        }
                    }
                } else {
                    for (verseId in verseIds) {
                        db.contentQueries.deleteMemorizationByVerse(verseId)
                    }
                }
            }
        }

    override fun observeMemorizedVerseIds(matnId: String): Flow<Set<String>> =
        db.contentQueries
            .selectMemorizedVerseIdsByMatn(matnId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { it.toSet() }

    override fun observeMatnProgress(matnId: String): Flow<MatnProgress> =
        db.contentQueries
            .selectMatnProgressById(matnId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row ->
                if (row == null) {
                    MatnProgress(matnId, 0, 0)
                } else {
                    MatnProgress(matnId, row.memorized_verses.toInt(), row.total_verses.toInt())
                }
            }

    override fun observeLibraryProgress(): Flow<List<MatnProgress>> =
        db.contentQueries
            .selectAllMatnProgress()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { MatnProgress(it.matn_id, it.memorized_verses.toInt(), it.total_verses.toInt()) } }

    override suspend fun recordPractice(verseId: String): Resource<Unit> =
        storageCall({ "Failed to record practice for verse $verseId" }) {
            db.contentQueries.insertDailyPractice(newId(), today(), verseId, clock())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTodayPracticeCount(): Flow<Int> =
        flow {
            while (true) {
                emit(today())
                delay(dayCheckIntervalMs.milliseconds)
            }
        }
            .distinctUntilChanged()
            .flatMapLatest { day ->
                db.contentQueries.selectDailyPracticeCount(day)
                    .asFlow()
                    .mapToOne(Dispatchers.Default)
                    .map { it.toInt() }
            }
}
