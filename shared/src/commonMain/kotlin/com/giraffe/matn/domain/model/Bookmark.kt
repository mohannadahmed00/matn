package com.giraffe.matn.domain.model

data class Bookmark(
    val id: String,
    val verseId: String,
    val createdAtMs: Long,
)

/** Global bookmarks-list item (Notes tab): the bookmark row plus its verse/matn context. */
data class BookmarkEntry(
    val bookmark: Bookmark,
    val ref: AnnotatedVerseRef,
)
