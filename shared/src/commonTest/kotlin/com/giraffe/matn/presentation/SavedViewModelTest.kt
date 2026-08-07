package com.giraffe.matn.presentation

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.Bookmark
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.MemorizedEntry
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.domain.usecase.SaveNoteParams
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.presentation.saved.SavedFilter
import com.giraffe.matn.presentation.saved.SavedRow
import com.giraffe.matn.presentation.saved.SavedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Saved tab replaced three surfaces with one filtered route, so what needs covering is the
 * filtering, the interleaved ordering, and the removal/undo pairing.
 */
class SavedViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun ref(verseId: String, number: Int) = AnnotatedVerseRef(
        matnId = "m1",
        matnTitle = "الأجرومية",
        verseId = verseId,
        verseNumber = number,
        verseText = "نص البيت",
    )

    private val bookmark = BookmarkEntry(Bookmark("b1", "v3", createdAtMs = 300), ref("v3", 3))
    private val note = NoteEntry(Note("n1", "v4", "ملاحظة", updatedAtMs = 200), ref("v4", 4))
    private val memorized = MemorizedEntry(id = "z1", memorizedAtMs = 100, ref = ref("v1", 1))

    private class RecordingUseCase<P, R>(private val result: R) : UseCase<P, R> {
        val calls = mutableListOf<P>()
        override suspend fun invoke(params: P): Resource<R> {
            calls += params
            return Resource.Success(result)
        }
    }

    private class FailingUseCase<P, R> : UseCase<P, R> {
        override suspend fun invoke(params: P): Resource<R> =
            Resource.Failure(com.giraffe.matn.core.AppError.Storage("nope"))
    }

    private fun <T> flowUseCase(value: T) = object : FlowUseCase<Unit, T> {
        override fun invoke(params: Unit): Flow<T> = flowOf(value)
    }

    private fun newViewModel(
        bookmarks: List<BookmarkEntry> = listOf(bookmark),
        notes: List<NoteEntry> = listOf(note),
        memorizedEntries: List<MemorizedEntry> = listOf(memorized),
        toggleBookmark: UseCase<String, Boolean> = RecordingUseCase(false),
        deleteNote: UseCase<String, Unit> = RecordingUseCase(Unit),
        saveNote: UseCase<SaveNoteParams, Note> = RecordingUseCase(note.note),
        toggleMemorized: UseCase<ToggleVerseMemorizedUseCase.Params, Unit> = RecordingUseCase(Unit),
    ) = SavedViewModel(
        observeBookmarks = flowUseCase(bookmarks),
        observeNotes = flowUseCase(notes),
        observeMemorized = flowUseCase(memorizedEntries),
        toggleBookmark = toggleBookmark,
        deleteNote = deleteNote,
        saveNote = saveNote,
        toggleVerseMemorized = toggleMemorized,
    )

    @Test
    fun all_segment_interleaves_the_three_kinds_newest_first() = runTest {
        val state = newViewModel().state.value

        assertEquals(3, state.allCount)
        assertEquals(
            listOf("bookmark:b1", "note:n1", "memorized:z1"),
            state.rows.map { it.key },
        )
    }

    @Test
    fun segments_filter_in_place_without_touching_the_underlying_lists() = runTest {
        val viewModel = newViewModel()

        viewModel.onFilterSelected(SavedFilter.NOTES)

        val state = viewModel.state.value
        assertEquals(listOf("note:n1"), state.rows.map { it.key })
        // Counts stay whole-collection so the segment labels do not depend on the selection.
        assertEquals(3, state.allCount)
        assertEquals(1, state.memorizedCount)
    }

    @Test
    fun each_segment_reports_its_own_count() = runTest {
        val state = newViewModel().state.value

        assertEquals(3, state.count(SavedFilter.ALL))
        assertEquals(1, state.count(SavedFilter.NOTES))
        assertEquals(1, state.count(SavedFilter.MEMORIZED))
    }

    @Test
    fun removing_a_bookmark_toggles_it_off_and_arms_undo() = runTest {
        val toggle = RecordingUseCase<String, Boolean>(false)
        val viewModel = newViewModel(toggleBookmark = toggle)

        viewModel.onRemove(SavedRow.Bookmarked(bookmark))

        assertEquals(listOf("v3"), toggle.calls)
        assertEquals(SavedRow.Bookmarked(bookmark), viewModel.state.value.pendingRemoval?.row)
    }

    @Test
    fun removing_a_note_deletes_it_by_id() = runTest {
        val delete = RecordingUseCase<String, Unit>(Unit)
        val viewModel = newViewModel(deleteNote = delete)

        viewModel.onRemove(SavedRow.Noted(note))

        assertEquals(listOf("n1"), delete.calls)
    }

    @Test
    fun removing_a_memorized_row_unmarks_the_verse() = runTest {
        val toggle = RecordingUseCase<ToggleVerseMemorizedUseCase.Params, Unit>(Unit)
        val viewModel = newViewModel(toggleMemorized = toggle)

        viewModel.onRemove(SavedRow.Memorized(memorized))

        assertEquals(listOf(ToggleVerseMemorizedUseCase.Params("v1", memorized = false)), toggle.calls)
    }

    /** A removal that failed never offers an Undo — there is nothing to put back. */
    @Test
    fun a_failed_removal_does_not_arm_undo() = runTest {
        val viewModel = newViewModel(deleteNote = FailingUseCase())

        viewModel.onRemove(SavedRow.Noted(note))

        assertNull(viewModel.state.value.pendingRemoval)
    }

    @Test
    fun undo_restores_a_deleted_note_with_its_original_text() = runTest {
        val save = RecordingUseCase<SaveNoteParams, Note>(note.note)
        val viewModel = newViewModel(saveNote = save)

        viewModel.onRemove(SavedRow.Noted(note))
        viewModel.onUndo()

        assertEquals(listOf(SaveNoteParams("v4", "ملاحظة")), save.calls)
        assertNull(viewModel.state.value.pendingRemoval)
    }

    @Test
    fun undo_re_marks_a_memorized_verse() = runTest {
        val toggle = RecordingUseCase<ToggleVerseMemorizedUseCase.Params, Unit>(Unit)
        val viewModel = newViewModel(toggleMemorized = toggle)

        viewModel.onRemove(SavedRow.Memorized(memorized))
        viewModel.onUndo()

        assertEquals(
            listOf(
                ToggleVerseMemorizedUseCase.Params("v1", memorized = false),
                ToggleVerseMemorizedUseCase.Params("v1", memorized = true),
            ),
            toggle.calls,
        )
    }

    /** The snackbar timing out is not an undo: the removal stands and nothing is re-issued. */
    @Test
    fun dismissing_the_snackbar_clears_undo_without_restoring() = runTest {
        val save = RecordingUseCase<SaveNoteParams, Note>(note.note)
        val viewModel = newViewModel(saveNote = save)

        viewModel.onRemove(SavedRow.Noted(note))
        viewModel.onUndoDismissed()

        assertTrue(save.calls.isEmpty())
        assertNull(viewModel.state.value.pendingRemoval)
    }

    @Test
    fun tapping_a_row_emits_its_matn_and_verse() = runTest {
        val viewModel = newViewModel()

        viewModel.onRowClick(SavedRow.Memorized(memorized))

        assertEquals("m1" to "v1", viewModel.navigation.first())
    }
}
