package com.giraffe.matn.presentation.notes

import com.giraffe.matn.domain.model.BookmarkEntry

/** US2 (this task, T035): bookmarks only. US3 (T047) adds the `notes` list. */
data class NotesTabUiState(
    val bookmarks: List<BookmarkEntry> = emptyList(),
)
