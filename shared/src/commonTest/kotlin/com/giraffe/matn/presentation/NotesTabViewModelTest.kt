package com.giraffe.matn.presentation

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.Bookmark
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.presentation.notes.NotesTabViewModel
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
import kotlin.test.assertTrue

class NotesTabViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun bookmarkEntry(verseId: String) = BookmarkEntry(
        bookmark = Bookmark(id = "b-$verseId", verseId = verseId, createdAtMs = 1000),
        ref = AnnotatedVerseRef(matnId = "m1", matnTitle = "matn", verseId = verseId, verseNumber = 1, verseText = "text"),
    )

    private fun noteEntry(verseId: String) = NoteEntry(
        note = Note(id = "n-$verseId", verseId = verseId, text = "note text", updatedAtMs = 1000),
        ref = AnnotatedVerseRef(matnId = "m1", matnTitle = "matn", verseId = verseId, verseNumber = 1, verseText = "text"),
    )

    private fun vm(
        bookmarks: List<BookmarkEntry> = emptyList(),
        notes: List<NoteEntry> = emptyList(),
    ) = NotesTabViewModel(
        observeBookmarks = FakeObserveBookmarks { flowOf(bookmarks) },
        observeNotes = FakeObserveNotes { flowOf(notes) },
    )

    @Test
    fun `both empty`() = runTest {
        val viewModel = vm()
        assertTrue(viewModel.state.value.bookmarks.isEmpty())
        assertTrue(viewModel.state.value.notes.isEmpty())
    }

    @Test
    fun `bookmarks only`() = runTest {
        val entries = listOf(bookmarkEntry("v1"))
        val viewModel = vm(bookmarks = entries)
        assertEquals(entries, viewModel.state.value.bookmarks)
        assertTrue(viewModel.state.value.notes.isEmpty())
    }

    @Test
    fun `notes only`() = runTest {
        val entries = listOf(noteEntry("v1"))
        val viewModel = vm(notes = entries)
        assertTrue(viewModel.state.value.bookmarks.isEmpty())
        assertEquals(entries, viewModel.state.value.notes)
    }

    @Test
    fun `both populated`() = runTest {
        val bookmarks = listOf(bookmarkEntry("v1"))
        val notes = listOf(noteEntry("v2"))
        val viewModel = vm(bookmarks = bookmarks, notes = notes)
        assertEquals(bookmarks, viewModel.state.value.bookmarks)
        assertEquals(notes, viewModel.state.value.notes)
    }

    @Test
    fun `bookmark click emits matnId and verseId`() = runTest {
        val viewModel = vm(bookmarks = listOf(bookmarkEntry("v1")))
        viewModel.onBookmarkClick(bookmarkEntry("v1"))
        val (matnId, verseId) = viewModel.navigation.first()
        assertEquals("m1", matnId)
        assertEquals("v1", verseId)
    }

    @Test
    fun `note click emits matnId and verseId`() = runTest {
        val viewModel = vm(notes = listOf(noteEntry("v1")))
        viewModel.onNoteClick(noteEntry("v1"))
        val (matnId, verseId) = viewModel.navigation.first()
        assertEquals("m1", matnId)
        assertEquals("v1", verseId)
    }

    private class FakeObserveBookmarks(
        private val block: (Unit) -> Flow<List<BookmarkEntry>>,
    ) : FlowUseCase<Unit, List<BookmarkEntry>> {
        override fun invoke(params: Unit): Flow<List<BookmarkEntry>> = block(params)
    }

    private class FakeObserveNotes(
        private val block: (Unit) -> Flow<List<NoteEntry>>,
    ) : FlowUseCase<Unit, List<NoteEntry>> {
        override fun invoke(params: Unit): Flow<List<NoteEntry>> = block(params)
    }
}
