package com.giraffe.matn.presentation.settings

import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.ThemeMode

/**
 * Immutable UI state for the Settings tab (T065, storage-ui-contract.md §2). Storage section sits
 * at the very top so SC-008 holds: find the figure within 10 s, without scrolling past unrelated
 * preferences. New preferences go **below** the storage section (rule 4, SC-008).
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val totalUsedBytes: Long = 0L,
    val onDemandUsedBytes: Long = 0L,
    val freeSpaceBytes: Long = 0L,
    /** Size-ordered installed-matn breakdown (FR-025); the starter is included, non-removable. */
    val entries: List<MatnStorageEntry> = emptyList(),
    /** Drives [com.giraffe.matn.presentation.common.ConfirmRemovalDialog]; `null` when closed. */
    val pendingRemoval: RemovalTarget? = null,
    /** The most recent removal's outcome, so post-removal copy states honestly what happened
     *  (`Reclaimed` vs `ReleasedPendingSystemReclaim`, research D2). `null` before any removal. */
    val lastOutcome: RemovalOutcome? = null,
    /** T043 (US1) — the persisted appearance preference (FR-006); default `SYSTEM`. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** T077 (US3) — the OS-level notification-permission status (FR-023); re-read on resume so a
     *  change made outside the app (e.g. revoked in device settings) is reflected. */
    val notificationStatus: PermissionStatus = PermissionStatus.NOT_DETERMINED,
) {
    /** FR-028: nothing on-demand installed yet — the starter alone never lifts this off zero. */
    val isOnDemandEmpty: Boolean get() = onDemandUsedBytes == 0L
}

/** What [SettingsUiState.pendingRemoval] confirms — one matn, or every on-demand matn at once. */
sealed interface RemovalTarget {
    data class SingleMatn(val matnId: String, val title: String, val bytes: Long) : RemovalTarget
    data class AllContent(val totalBytes: Long) : RemovalTarget
}
