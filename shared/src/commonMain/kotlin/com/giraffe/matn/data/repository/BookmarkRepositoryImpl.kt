package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.mapper.toBookmarkEntry
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.repository.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLDelight-backed [BookmarkRepository] (annotations-contract.md; research.md D4 — [clock] and
 * [newId] are injected pure lambdas; production values are supplied only at the Koin wiring site,
 * tests inject fixed ones). `toggle` reads-then-writes inside one transaction so `UNIQUE(verse_id)`
 * plus this serialization make rapid repeated toggles settle to a single consistent state.
 */
class BookmarkRepositoryImpl(
    private val db: ContentDatabase,
    private val clock: () -> Long,
    private val newId: () -> String,
) : BookmarkRepository {

    override suspend fun toggle(verseId: String): Resource<Boolean> =
        storageCall({ "Failed to toggle bookmark for verse $verseId" }) {
            db.transactionWithResult {
                val existing = db.contentQueries.selectBookmarkByVerse(verseId).executeAsOneOrNull()
                if (existing != null) {
                    db.contentQueries.deleteBookmarkByVerse(verseId)
                    false
                } else {
                    db.contentQueries.insertBookmark(newId(), verseId, clock())
                    true
                }
            }
        }

    override fun observeAll(): Flow<List<BookmarkEntry>> =
        db.contentQueries
            .selectAllBookmarksWithContext()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toBookmarkEntry() } }

    override fun observeBookmarkedVerseIds(matnId: String): Flow<Set<String>> =
        db.contentQueries
            .selectBookmarkedVerseIdsByMatn(matnId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { it.toSet() }
}
