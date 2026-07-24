package com.giraffe.matn.presentation.notes

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Owns the Notes tab state (US2 FR-012/FR-020; US3 T047 adds the notes list). Entry taps emit a
 * one-shot `(matnId, verseId)` navigation pair via [navigation] (same idiom as
 * [com.giraffe.matn.presentation.home.HomeViewModel]'s `navigation` channel), keeping
 * `androidx.navigation` out of the ViewModel (Principle II).
 */
class NotesTabViewModel(
    observeBookmarks: FlowUseCase<Unit, List<BookmarkEntry>>,
) : BaseViewModel<NotesTabUiState>(NotesTabUiState()) {

    private val _navigation = Channel<Pair<String, String>>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    init {
        observeBookmarks.invoke(Unit)
            .onEach { entries -> setState { it.copy(bookmarks = entries) } }
            .launchIn(viewModelScope)
    }

    fun onBookmarkClick(entry: BookmarkEntry) {
        viewModelScope.launch { _navigation.send(entry.ref.matnId to entry.ref.verseId) }
    }
}
