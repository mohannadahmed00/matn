package com.giraffe.matn.teacher

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giraffe.matn.teacher.presentation.common.rememberScopedViewModelStoreOwner
import kotlin.test.Test
import kotlin.test.assertTrue

private data class Doc(val id: String, val title: String)

/** Stands in for `EditorViewModel`: seeded once, at construction. */
private class DocViewModel(val doc: Doc) : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() { cleared = true }
}

/** Stands in for `EditorScreen`: `viewModel(key = <content id>)`, which only runs its factory when
 * the store has nothing under that key. */
@Composable
private fun DocScreen(doc: Doc, onViewModel: (DocViewModel) -> Unit = {}) {
    val vm: DocViewModel = viewModel(key = doc.id) { DocViewModel(doc) }
    onViewModel(vm)
    Text("TITLE[${vm.doc.title}]")
}

class ScopedViewModelStoreOwnerTest {

    /**
     * Regression for a shipped bug. The desktop portal has one process-wide `ViewModelStore`, so
     * `viewModel(key = draft.id)` returned the ViewModel built the *first* time that id was shown
     * and quietly ignored the draft just fetched from the server — opening a matn from the library
     * showed a stale, sometimes blank, editor.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a new epoch re-seeds the screen from the object it is given`() = runComposeUiTest {
        var epoch by mutableStateOf(0)
        var doc by mutableStateOf(Doc("matn-1", ""))
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides rememberScopedViewModelStoreOwner(epoch)) {
                DocScreen(doc)
            }
        }
        onNodeWithText("TITLE[]").assertExists()

        // Same id, newer content — exactly "reopen this matn after loading it from the server".
        doc = Doc("matn-1", "FROM SERVER")
        epoch++
        waitForIdle()

        onNodeWithText("TITLE[FROM SERVER]").assertExists()
    }

    /** Without a new epoch the ViewModel must survive — leaving the editor for the library and
     * coming back may not discard work in progress. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the same epoch keeps the existing view model across a round trip`() = runComposeUiTest {
        val epoch = 0
        var onEditor by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides rememberScopedViewModelStoreOwner(epoch)) {
                if (onEditor) DocScreen(Doc("matn-1", "TYPED")) else Text("LIBRARY")
            }
        }
        onEditor = false
        waitForIdle()
        onEditor = true
        waitForIdle()

        onNodeWithText("TITLE[TYPED]").assertExists()
    }

    /**
     * Regression for a shipped bug. The library's ViewModel lived in the application-wide store, so
     * re-entering the screen rendered whatever the previous visit had ended on — a stale error, in
     * the report — before any new load could start: the teacher saw "server problem", then data,
     * having done nothing.
     *
     * Owning the store *inside* the screen is what fixes it: leaving composition disposes the owner
     * and clears the ViewModel, so the next visit starts from its initial state.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an owner created inside a screen gives each visit a fresh view model`() = runComposeUiTest {
        var onScreen by mutableStateOf(true)
        var current: DocViewModel? = null

        setContent {
            if (onScreen) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides rememberScopedViewModelStoreOwner(Unit)) {
                    DocScreen(Doc("matn-1", "loading")) { vm -> current = vm }
                }
            } else {
                Text("ELSEWHERE")
            }
        }
        val firstVisit = requireNotNull(current)

        onScreen = false // leave the library
        waitForIdle()
        onScreen = true // come back
        waitForIdle()

        assertTrue(firstVisit.cleared, "the previous visit's ViewModel outlived the screen")
        assertTrue(current !== firstVisit, "the new visit reused the previous visit's state")
    }

    /** The superseded ViewModel must be cleared, not merely orphaned: its `viewModelScope` runs the
     * autosave scheduler, and an editor left alive holding a stale draft can save over a newer one. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `superseding an epoch clears the previous view model`() = runComposeUiTest {
        var epoch by mutableStateOf(0)
        var first: DocViewModel? = null
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides rememberScopedViewModelStoreOwner(epoch)) {
                DocScreen(Doc("matn-1", "x")) { vm -> if (first == null) first = vm }
            }
        }
        waitForIdle()
        epoch++
        waitForIdle()

        assertTrue(first?.cleared == true, "the previous editor's ViewModel was never cleared")
    }
}
