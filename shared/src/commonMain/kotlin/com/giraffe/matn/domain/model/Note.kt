package com.giraffe.matn.domain.model

data class Note(
    val id: String,
    val verseId: String,
    val text: String,
    val updatedAtMs: Long,
)

/** Global notes-list item (Notes tab): the note row plus its verse/matn context. */
data class NoteEntry(
    val note: Note,
    val ref: AnnotatedVerseRef,
)
