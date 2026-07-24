package com.giraffe.matn.presentation.notes

import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.NoteEntry

data class NotesTabUiState(
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val notes: List<NoteEntry> = emptyList(),
)
