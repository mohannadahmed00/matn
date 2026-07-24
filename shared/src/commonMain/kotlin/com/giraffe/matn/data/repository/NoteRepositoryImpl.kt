package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.mapper.toDomain
import com.giraffe.matn.data.mapper.toNoteEntry
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.error.NoteError
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLDelight-backed [NoteRepository] (annotations-contract.md; research.md D4 — [clock]/[newId]
 * mirror [BookmarkRepositoryImpl]'s conventions). `save` mints a fresh id only when no row exists
 * yet for the verse — the T005 `upsertNote` ON CONFLICT keeps the original id, refreshing only
 * `text`/`updated_at`.
 */
class NoteRepositoryImpl(
    private val db: ContentDatabase,
    private val clock: () -> Long,
    private val newId: () -> String,
) : NoteRepository {

    override suspend fun save(verseId: String, text: String): Resource<Note> {
        if (text.isBlank()) return Resource.Failure(NoteError.EmptyNote)
        return storageCall({ "Failed to save note for verse $verseId" }) {
            db.transactionWithResult {
                val existingId = db.contentQueries.selectNoteByVerse(verseId).executeAsOneOrNull()?.id
                val id = existingId ?: newId()
                val updatedAt = clock()
                db.contentQueries.upsertNote(id, verseId, text, updatedAt)
                Note(id = id, verseId = verseId, text = text, updatedAtMs = updatedAt)
            }
        }
    }

    override suspend fun delete(verseId: String): Resource<Unit> =
        storageCall({ "Failed to delete note for verse $verseId" }) {
            db.contentQueries.deleteNoteByVerse(verseId)
        }

    override suspend fun get(verseId: String): Resource<Note?> =
        storageCall({ "Failed to read note for verse $verseId" }) {
            db.contentQueries.selectNoteByVerse(verseId).executeAsOneOrNull()?.toDomain()
        }

    override fun observeAll(): Flow<List<NoteEntry>> =
        db.contentQueries
            .selectAllNotesWithContext()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toNoteEntry() } }

    override fun observeNotedVerseIds(matnId: String): Flow<Set<String>> =
        db.contentQueries
            .selectNotedVerseIdsByMatn(matnId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { it.toSet() }
}
