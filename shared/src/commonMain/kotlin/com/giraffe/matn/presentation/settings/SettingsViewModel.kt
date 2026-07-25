package com.giraffe.matn.presentation.settings

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Owns the Settings storage-management state (T066, storage-ui-contract.md §2; Principle II).
 * Mirrors [com.giraffe.matn.presentation.goals.GoalsViewModel]'s shape: a single collector clears
 * [SettingsUiState.isLoading] and fills the breakdown; removal intents are gated behind explicit
 * confirmation (FR-018) and never fire directly from [onRemoveMatn]/[onRemoveAll].
 */
class SettingsViewModel(
    observeStorageUsage: FlowUseCase<Unit, StorageUsage>,
    private val removeMatnContent: UseCase<String, RemovalOutcome>,
    private val removeAllContent: UseCase<Unit, List<RemovalOutcome>>,
) : BaseViewModel<SettingsUiState>(SettingsUiState()) {

    init {
        observeStorageUsage.invoke(Unit)
            .onEach { usage ->
                setState {
                    it.copy(
                        isLoading = false,
                        totalUsedBytes = usage.totalUsedBytes,
                        onDemandUsedBytes = usage.onDemandUsedBytes,
                        freeSpaceBytes = usage.freeSpaceBytes,
                        entries = usage.entries,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** FR-018: open the confirmation for one matn. Removal never fires without it. */
    fun onRemoveMatn(matnId: String) {
        val entry = stateValue.entries.firstOrNull { it.matnId == matnId } ?: return
        setState {
            it.copy(pendingRemoval = RemovalTarget.SingleMatn(entry.matnId, entry.title, entry.bytes))
        }
    }

    /** FR-018/FR-029: open the confirmation for "remove all downloaded content". */
    fun onRemoveAll() {
        setState { it.copy(pendingRemoval = RemovalTarget.AllContent(stateValue.onDemandUsedBytes)) }
    }

    /** FR-018: dismiss without removing. */
    fun onDismissRemoval() {
        setState { it.copy(pendingRemoval = null) }
    }

    /** FR-017/FR-021/FR-027/FR-029: confirmed removal, one matn or every on-demand matn.
     *  [observeStorageUsage]'s re-emission updates the total; [SettingsUiState.lastOutcome]
     *  carries the platform-honest outcome. */
    fun onConfirmRemoval() {
        when (val target = stateValue.pendingRemoval) {
            is RemovalTarget.SingleMatn -> {
                setState { it.copy(pendingRemoval = null) }
                runUseCase(
                    useCase = removeMatnContent,
                    params = target.matnId,
                    onSuccess = { outcome -> setState { it.copy(lastOutcome = outcome) } },
                    onError = {/* best-effort; the breakdown re-derives from the repository */ },
                )
            }
            is RemovalTarget.AllContent -> {
                setState { it.copy(pendingRemoval = null) }
                runUseCase(
                    useCase = removeAllContent,
                    params = Unit,
                    onSuccess = { outcomes -> setState { it.copy(lastOutcome = outcomes.lastOrNull()) } },
                    onError = {/* best-effort; the breakdown re-derives from the repository */ },
                )
            }
            null -> Unit
        }
    }
}
