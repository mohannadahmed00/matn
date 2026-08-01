package com.giraffe.matn.teacher

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

/** Stands in for `EditorViewModel`: outlives a nested screen and holds the state it wrote. */
private class HostViewModel : ViewModel() {
    var value = "initial"
    var cleared = false
        private set

    override fun onCleared() { cleared = true }
}

/** Stands in for `SplitViewModel`: a different type, shown over the host for a while. */
private class NestedViewModel : ViewModel()

/**
 * Regression for a shipped bug. `EditorScreen` and the `SplitScreen` nested inside it both called
 * `viewModel(key = draft.id)`. An **explicit** key is used verbatim as the `ViewModelStore` slot —
 * unlike the default key, it does not fold in the class name — so the two collided: opening the
 * split screen evicted the editor's ViewModel (and `ViewModelStore.put` clears what it replaces),
 * and closing it built a fresh editor from `initialDraft`.
 *
 * The visible symptom was that a successful split returned to an editor showing no audio, as though
 * the upload had failed, until the teacher left the screen and came back.
 */
class ViewModelKeyCollisionTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a nested screen does not evict the host's view model`() = runComposeUiTest {
        var showNested by mutableStateOf(false)
        var current: HostViewModel? = null

        setContent {
            val host: HostViewModel = viewModel(key = "editor:matn-1") { HostViewModel() }
            current = host
            Text("HOST[${host.value}]")
            if (showNested) {
                viewModel<NestedViewModel>(key = "split:matn-1") { NestedViewModel() }
                Text("NESTED")
            }
        }
        val host = requireNotNull(current)
        host.value = "written by the nested screen"

        showNested = true
        waitForIdle()
        assertFalse(host.cleared, "opening the nested screen cleared the host's ViewModel")

        showNested = false
        waitForIdle()

        assertSame(host, current, "the host's ViewModel was rebuilt instead of being kept")
        assertSame("written by the nested screen", current?.value)
    }
}
