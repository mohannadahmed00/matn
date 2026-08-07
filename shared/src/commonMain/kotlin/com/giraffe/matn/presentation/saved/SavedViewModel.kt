package com.giraffe.matn.presentation.saved

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.MemorizedEntry
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.domain.usecase.SaveNoteParams
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Owns the Saved tab — the single route that absorbed the old Bookmarks, Notes and Memorized
 * surfaces (Matn Design System §02, *Navigation consolidation*). Successor to `NotesTabViewModel`.
 *
 * Row taps emit a one-shot `(matnId, verseId)` pair via [navigation], keeping `androidx.navigation`
 * out of the ViewModel (Principle II) — the same idiom
 * [com.giraffe.matn.presentation.home.HomeViewModel] uses.
 *
 * Removal is **not** optimistic: [onRemove] performs the real delete and the reactive query drops
 * the row, then [state]'s `pendingRemoval` offers [onUndo] for the snackbar's lifetime. That way an
 * undo that fails leaves the list telling the truth, and there is no phantom row to reconcile.
 */
class SavedViewModel(
    observeBookmarks: FlowUseCase<Unit, List<BookmarkEntry>>,
    observeNotes: FlowUseCase<Unit, List<NoteEntry>>,
    observeMemorized: FlowUseCase<Unit, List<MemorizedEntry>>,
    private val toggleBookmark: UseCase<String, Boolean>,
    private val deleteNote: UseCase<String, Unit>,
    private val saveNote: UseCase<SaveNoteParams, com.giraffe.matn.domain.model.Note>,
    private val toggleVerseMemorized: UseCase<ToggleVerseMemorizedUseCase.Params, Unit>,
) : BaseViewModel<SavedUiState>(SavedUiState()) {

    private val _navigation = Channel<Pair<String, String>>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    init {
        observeBookmarks.invoke(Unit)
            .onEach { entries -> setState { it.copy(bookmarks = entries) } }
            .launchIn(viewModelScope)
        observeNotes.invoke(Unit)
            .onEach { entries -> setState { it.copy(notes = entries) } }
            .launchIn(viewModelScope)
        observeMemorized.invoke(Unit)
            .onEach { entries -> setState { it.copy(memorized = entries) } }
            .launchIn(viewModelScope)
    }

    fun onFilterSelected(filter: SavedFilter) {
        setState { it.copy(filter = filter) }
    }

    fun onRowClick(row: SavedRow) {
        viewModelScope.launch { _navigation.send(row.ref.matnId to row.ref.verseId) }
    }

    /** Swipe-to-remove. The row kind decides which store the removal lands in. */
    fun onRemove(row: SavedRow) {
        val verseId = row.ref.verseId
        when (row) {
            is SavedRow.Bookmarked -> runUseCase(
                useCase = toggleBookmark,
                params = verseId,
                onSuccess = { armUndo(row) },
                onError = { /* nothing was removed; the list already shows the truth */ },
            )

            is SavedRow.Noted -> runUseCase(
                useCase = deleteNote,
                params = row.entry.note.id,
                onSuccess = { armUndo(row) },
                onError = { },
            )

            is SavedRow.Memorized -> runUseCase(
                useCase = toggleVerseMemorized,
                params = ToggleVerseMemorizedUseCase.Params(verseId, memorized = false),
                onSuccess = { armUndo(row) },
                onError = { },
            )
        }
    }

    /**
     * Puts back exactly what [onRemove] took out. Un-marking a verse memorized leaves the day's
     * practice credit alone (FR-014), so re-marking it here is a true inverse rather than a
     * second credit.
     */
    fun onUndo() {
        val row = stateValue.pendingRemoval?.row ?: return
        clearUndo()
        val verseId = row.ref.verseId
        when (row) {
            is SavedRow.Bookmarked ->
                runUseCase(toggleBookmark, verseId, onSuccess = { }, onError = { })

            is SavedRow.Noted -> runUseCase(
                useCase = saveNote,
                params = SaveNoteParams(verseId, row.entry.note.text),
                onSuccess = { },
                onError = { },
            )

            is SavedRow.Memorized -> runUseCase(
                useCase = toggleVerseMemorized,
                params = ToggleVerseMemorizedUseCase.Params(verseId, memorized = true),
                onSuccess = { },
                onError = { },
            )
        }
    }

    /** The snackbar timed out or was dismissed — the removal stands. */
    fun onUndoDismissed() = clearUndo()

    private fun armUndo(row: SavedRow) {
        setState { it.copy(pendingRemoval = PendingRemoval(row)) }
    }

    private fun clearUndo() {
        setState { it.copy(pendingRemoval = null) }
    }
}
