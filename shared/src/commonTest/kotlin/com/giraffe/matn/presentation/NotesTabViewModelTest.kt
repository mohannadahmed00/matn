package com.giraffe.matn.presentation

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.Bookmark
import com.giraffe.matn.domain.model.BookmarkEntry
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

    private fun entry(verseId: String) = BookmarkEntry(
        bookmark = Bookmark(id = "b-$verseId", verseId = verseId, createdAtMs = 1000),
        ref = AnnotatedVerseRef(matnId = "m1", matnTitle = "matn", verseId = verseId, verseNumber = 1, verseText = "text"),
    )

    @Test
    fun `empty bookmarks state`() = runTest {
        val vm = NotesTabViewModel(FakeObserveBookmarks { flowOf(emptyList()) })
        assertTrue(vm.state.value.bookmarks.isEmpty())
    }

    @Test
    fun `populated bookmarks state`() = runTest {
        val entries = listOf(entry("v1"), entry("v2"))
        val vm = NotesTabViewModel(FakeObserveBookmarks { flowOf(entries) })
        assertEquals(entries, vm.state.value.bookmarks)
    }

    @Test
    fun `bookmark click emits matnId and verseId`() = runTest {
        val vm = NotesTabViewModel(FakeObserveBookmarks { flowOf(listOf(entry("v1"))) })
        vm.onBookmarkClick(entry("v1"))
        val (matnId, verseId) = vm.navigation.first()
        assertEquals("m1", matnId)
        assertEquals("v1", verseId)
    }

    private class FakeObserveBookmarks(
        private val block: (Unit) -> Flow<List<BookmarkEntry>>,
    ) : FlowUseCase<Unit, List<BookmarkEntry>> {
        override fun invoke(params: Unit): Flow<List<BookmarkEntry>> = block(params)
    }
}
