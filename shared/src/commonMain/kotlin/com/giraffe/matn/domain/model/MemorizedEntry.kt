package com.giraffe.matn.domain.model

/**
 * Global memorized-list item (Saved tab, *Memorized* segment): the memorization row plus its
 * verse/matn context — the same shape [BookmarkEntry] and [NoteEntry] already have, so the three
 * Saved segments render from one row type.
 *
 * [memorizedAtMs] is the moment practice confirmed the verse. The Saved surface states *when*
 * rather than a percentage or a rank, per the design system's Saved screen.
 */
data class MemorizedEntry(
    val id: String,
    val memorizedAtMs: Long,
    val ref: AnnotatedVerseRef,
)
