package com.giraffe.matn.presentation.saved

import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.MemorizedEntry
import com.giraffe.matn.domain.model.NoteEntry

/**
 * The Saved tab's segments (Matn Design System §05 — *Saved · three former screens, one route*).
 *
 * Deliberately three, not four: the design gives Bookmarks no segment of its own. Bookmarks are
 * "verses I have touched" with nothing else attached, so they carry no filterable content — they
 * appear under [ALL], tagged, and filtering to them alone would be a segment whose result is
 * "everything with no note and no memorized mark", which is not a thing a student looks for.
 */
enum class SavedFilter { ALL, NOTES, MEMORIZED }

/**
 * One row of the Saved list. The three kinds share a verse/matn [ref] so the list renders from one
 * row type and one sort order, which is what lets the segments filter *in place* rather than
 * swapping three differently-shaped lists.
 *
 * [recencyMs] is the row's own timestamp — bookmark creation, note update, memorization
 * confirmation — so [ALL][SavedFilter.ALL] interleaves the three kinds by when they last mattered.
 */
sealed interface SavedRow {
    val key: String
    val ref: AnnotatedVerseRef
    val recencyMs: Long

    data class Bookmarked(val entry: BookmarkEntry) : SavedRow {
        override val key: String get() = "bookmark:${entry.bookmark.id}"
        override val ref: AnnotatedVerseRef get() = entry.ref
        override val recencyMs: Long get() = entry.bookmark.createdAtMs
    }

    data class Noted(val entry: NoteEntry) : SavedRow {
        override val key: String get() = "note:${entry.note.id}"
        override val ref: AnnotatedVerseRef get() = entry.ref
        override val recencyMs: Long get() = entry.note.updatedAtMs
    }

    data class Memorized(val entry: MemorizedEntry) : SavedRow {
        override val key: String get() = "memorized:${entry.id}"
        override val ref: AnnotatedVerseRef get() = entry.ref
        override val recencyMs: Long get() = entry.memorizedAtMs
    }
}

/**
 * A removal awaiting its Undo window. Held in UI state rather than performed optimistically: the
 * delete has already happened in the database (the reactive query has already dropped the row), so
 * this is what [SavedViewModel] needs to put it back.
 */
data class PendingRemoval(val row: SavedRow)

/**
 * Saved tab state. [rows] is derived, never stored — the three lists are the source of truth and
 * the segment is a view onto them, which is the whole point of filtering in place.
 */
data class SavedUiState(
    val filter: SavedFilter = SavedFilter.ALL,
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val notes: List<NoteEntry> = emptyList(),
    val memorized: List<MemorizedEntry> = emptyList(),
    val pendingRemoval: PendingRemoval? = null,
) {
    val allCount: Int get() = bookmarks.size + notes.size + memorized.size
    val noteCount: Int get() = notes.size
    val memorizedCount: Int get() = memorized.size

    fun count(filter: SavedFilter): Int = when (filter) {
        SavedFilter.ALL -> allCount
        SavedFilter.NOTES -> noteCount
        SavedFilter.MEMORIZED -> memorizedCount
    }

    /** Newest first, with [key] as the tie-break so the order is total and stable across emissions. */
    val rows: List<SavedRow>
        get() {
            val source: List<SavedRow> = when (filter) {
                SavedFilter.ALL ->
                    bookmarks.map(SavedRow::Bookmarked) +
                        notes.map(SavedRow::Noted) +
                        memorized.map(SavedRow::Memorized)

                SavedFilter.NOTES -> notes.map(SavedRow::Noted)
                SavedFilter.MEMORIZED -> memorized.map(SavedRow::Memorized)
            }
            return source.sortedWith(compareByDescending<SavedRow> { it.recencyMs }.thenBy { it.key })
        }

    val isEmpty: Boolean get() = rows.isEmpty()
}
