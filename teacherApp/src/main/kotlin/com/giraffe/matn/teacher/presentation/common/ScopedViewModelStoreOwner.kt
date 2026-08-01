package com.giraffe.matn.teacher.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * A [ViewModelStoreOwner] that lives exactly as long as [key] stays the same, and clears itself the
 * moment [key] changes.
 *
 * The desktop portal has no navigation library, so every screen shares one application-wide
 * `ViewModelStore` that is never cleared. `viewModel(key = …)` only calls its factory when nothing
 * is stored under that key — so handing a screen a *newer* object with the same id silently gets
 * the old ViewModel and the old state. That is a real bug the teacher tool shipped: reopening a
 * matn from the library returned whatever the editor held the first time, ignoring what had just
 * been fetched from the server.
 *
 * Scoping the store to an "open" rather than to the process fixes both halves of that: the new open
 * gets a genuinely fresh ViewModel seeded from the loaded draft, and the previous one is cleared —
 * which cancels its `viewModelScope`, and with it an autosave scheduler that would otherwise stay
 * alive holding a stale draft.
 *
 * [key] must change **only** when a new instance is genuinely wanted. Recomposition, keystrokes and
 * leaving/re-entering the screen must all keep the same key, or in-progress edits are discarded.
 */
@Composable
fun rememberScopedViewModelStoreOwner(key: Any): ViewModelStoreOwner {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}
