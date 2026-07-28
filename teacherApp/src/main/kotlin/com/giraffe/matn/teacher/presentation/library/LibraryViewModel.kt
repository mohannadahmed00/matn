package com.giraffe.matn.teacher.presentation.library

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogLoadException
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.usecase.ListAuthoredMatnsUseCase
import com.giraffe.matn.domain.usecase.UnpublishMatnUseCase
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** `contracts/teacher-ui-contract.md` §3.3. */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val entries: List<CatalogEntry> = emptyList(),
    val error: RemoteError? = null,
    val pendingUnpublishId: String? = null,
)

class LibraryViewModel(
    private val listAuthoredMatns: ListAuthoredMatnsUseCase,
    private val unpublishMatn: UnpublishMatnUseCase,
) : BaseViewModel<LibraryUiState>(LibraryUiState()) {

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Cancels any in-flight load before starting a new one — otherwise a slow fetch kicked off on
     * screen entry can resolve *after* a same-session unpublish's own refresh and overwrite the
     * correct post-unpublish state with the stale pre-unpublish one (a classic last-response-wins
     * race, not last-request-wins). */
    fun load() {
        setState { it.copy(isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            listAuthoredMatns(Unit)
                .catch { t ->
                    val error = (t as? CatalogLoadException)?.error as? RemoteError ?: RemoteError.Decode
                    setState { it.copy(isLoading = false, error = error) }
                }
                .collect { entries -> setState { it.copy(isLoading = false, entries = entries, error = null) } }
        }
    }

    fun onRequestUnpublish(matnId: String) = setState { it.copy(pendingUnpublishId = matnId) }
    fun onDismissUnpublish() = setState { it.copy(pendingUnpublishId = null) }

    fun onConfirmUnpublish() {
        val matnId = stateValue.pendingUnpublishId ?: return
        setState { it.copy(pendingUnpublishId = null) }
        viewModelScope.launch {
            unpublishMatn(matnId)
            load()
        }
    }
}
