package com.giraffe.matn.presentation.settings

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.permission.NotificationPermission
import com.giraffe.matn.domain.usecase.GetFontSizeUseCase
import com.giraffe.matn.domain.usecase.ObserveThemeModeUseCase
import com.giraffe.matn.domain.usecase.SetFontSizeUseCase
import com.giraffe.matn.domain.usecase.SetThemeModeUseCase
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the Settings storage-management state (T066, storage-ui-contract.md §2; Principle II).
 * Mirrors [com.giraffe.matn.presentation.goals.GoalsViewModel]'s shape: a single collector clears
 * [SettingsUiState.isLoading] and fills the breakdown; removal intents are gated behind explicit
 * confirmation (FR-018) and never fire directly from [onRemoveMatn]/[onRemoveAll].
 *
 * T044 (US1): also owns the persisted appearance preference (FR-006). Appearance lives **below**
 * the storage section in the UI (rule 4, SC-008) and applies immediately on select.
 */
class SettingsViewModel(
    observeStorageUsage: FlowUseCase<Unit, StorageUsage>,
    private val removeMatnContent: UseCase<String, RemovalOutcome>,
    private val removeAllContent: UseCase<Unit, List<RemovalOutcome>>,
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val notificationPermission: NotificationPermission,
    getFontSize: GetFontSizeUseCase,
    private val setFontSize: SetFontSizeUseCase,
) : BaseViewModel<SettingsUiState>(SettingsUiState()) {

    init {
        // T077 (US3): populated on construction and re-read on every resume (see
        // SettingsScreen's LifecycleResumeEffect) so a change made outside the app is reflected.
        refreshPermissionStatus()
        observeStorageUsage.invoke(Unit)
            .onEach { usage ->
                setState {
                    it.copy(
                        isLoading = false,
                        totalUsedBytes = usage.totalUsedBytes,
                        freeSpaceBytes = usage.freeSpaceBytes,
                        entries = usage.entries,
                    )
                }
            }
            .launchIn(viewModelScope)

        // T044 (US1) — collect the persisted theme mode into state (FR-006). Existing storage
        // behaviour is untouched: this is a second, independent collector for a different use case.
        observeThemeMode.invoke(Unit)
            .onEach { mode -> setState { it.copy(themeMode = mode) } }
            .launchIn(viewModelScope)

        // Reading font size was previously reachable only from the details screen's unlabelled "أ"
        // affordance. It is a global preference (FR-016), so it belongs here too; both entry points
        // write through the same use case and observe the same flow, so they stay in step.
        getFontSize.invoke(Unit)
            .onEach { size -> setState { it.copy(fontSize = size) } }
            .launchIn(viewModelScope)
    }

    /** FR-016/FR-017: applies immediately and persists, exactly as the reading screen's control does. */
    fun onFontSizeSelected(size: ReadingFontSize) {
        setState { it.copy(fontSize = size) }
        runUseCase(
            useCase = setFontSize,
            params = size,
            onSuccess = { /* observed live; nothing extra to do */ },
            onError = { /* best-effort — re-emission from storage corrects state */ },
        )
    }

    /** T077 (US3, onboarding-permissions-contract.md §5): re-read the OS-level status. */
    fun refreshPermissionStatus() {
        viewModelScope.launch {
            val status = notificationPermission.status()
            setState { it.copy(notificationStatus = status) }
        }
    }

    /** FR-023: `PERMANENTLY_DENIED`'s only affordance — the OS no longer surfaces its own prompt. */
    fun onOpenNotificationSettings() = notificationPermission.openSystemSettings()

    /** FR-023: `DENIED`'s retry — an explicit, student-initiated re-ask from Settings, distinct
     *  from the cold-launch/onboarding request rule 5 forbids. */
    fun onRetryNotificationPermission() {
        viewModelScope.launch {
            val status = notificationPermission.request()
            setState { it.copy(notificationStatus = status) }
        }
    }

    /** FR-004/FR-006: applies immediately and persists. Re-selection is a no-op extra call. */
    fun onThemeModeSelected(mode: ThemeMode) {
        setState { it.copy(themeMode = mode) }
        runUseCase(
            useCase = setThemeMode,
            params = mode,
            onSuccess = { /* observed live; nothing extra to do */ },
            onError = { /* best-effort — re-emission from storage corrects state */ },
        )
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
        setState { it.copy(pendingRemoval = RemovalTarget.AllContent(stateValue.totalUsedBytes)) }
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
