package com.giraffe.matn.presentation.home

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.catalog.CatalogSyncState
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.model.MatnSummary

/**
 * Immutable UI state for the Home / library screen (data-model.md §4.1; Principle II).
 *
 *  * [isLoading] is true until the first catalog emission arrives.
 *  * [items] holds one [MatnSummary] per matn in the catalog — **downloaded or not** (Phase 13,
 *    FR-005). Before Phase 13 this listed only content on the device.
 *  * [continueLearning] is the Phase-4 Home offer. `null` renders **nothing at all** — no
 *    placeholder, no reserved space (FR-015). It stays null until its own collector resolves and
 *    MUST NOT gate the grid (SC-005/T043a).
 *  * [dailyGoal] (specs/010-design-system-adoption) is the top-bar progress ring's state.
 *  * [progressByMatn] (Phase 7, FR-006) is the per-matn memorized fraction for the library card
 *    affordance, keyed by matn id. Collected independently — like [continueLearning] — so it
 *    never gates [isLoading]; a matn absent from the map simply renders no progress affordance.
 *  * [availability] (FR-016) is the per-matn download state, keyed by matn id. Collected
 *    independently so it never gates [isLoading].
 *  * [syncState] / [isSyncing] (Phase 13, FR-006/FR-044) back the three empty-state cases below.
 */
/**
 * The library's in-place filter (Matn Design System §05 — the chip row above the grid).
 *
 * The design draws a third chip, *Grammar*. There is no category or subject field anywhere in the
 * domain — the teacher's authoring tool deliberately never added one — so that chip would filter on
 * nothing. It is omitted rather than faked.
 */
enum class LibraryFilter { ALL, ON_DEVICE }

data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<MatnSummary> = emptyList(),
    val error: AppError? = null,
    val continueLearning: ContinueLearningEntry? = null,
    val dailyGoal: DailyGoalUiState = DailyGoalUiState(),
    val progressByMatn: Map<String, Float> = emptyMap(),
    val availability: Map<String, ContentAvailability> = emptyMap(),
    val syncState: CatalogSyncState? = null,
    val isSyncing: Boolean = false,
    /**
     * Cover bytes keyed by matn id (FR-012), fetched opportunistically on the **browse** path only.
     * A matn absent from the map renders the placeholder — which is also what a failed fetch does,
     * because an unfetchable cover must never degrade anything else.
     */
    val covers: Map<String, ByteArray> = emptyMap(),
    /** Which chip is active above the grid. Presentation state, but it lives here so it survives
     *  the ViewModel rather than the composition, and so [visibleItems] stays derived. */
    val filter: LibraryFilter = LibraryFilter.ALL,
) {

    /** The grid's contents under the active [filter]. */
    val visibleItems: List<MatnSummary>
        get() = when (filter) {
            LibraryFilter.ALL -> items
            LibraryFilter.ON_DEVICE -> items.filter { availability[it.matn.id] is ContentAvailability.Downloaded }
        }

    /** How many متون are on the device — the count in the header's "N downloaded · X" summary. */
    val downloadedCount: Int
        get() = availability.values.count { it is ContentAvailability.Downloaded }

    /**
     * Bytes those متون occupy. Measured occupancy, not declared size: the header is reporting what
     * the device is actually giving up, which is the same number Settings' storage section shows.
     */
    val downloadedBytes: Long
        get() = availability.values.sumOf { (it as? ContentAvailability.Downloaded)?.occupiedBytes ?: 0L }

    /**
     * Whether the header carries its download summary. Suppressed at zero: "0 downloaded · 0 B" is
     * noise, and [showNothingDownloaded] already says that above the grid, in words that offer a
     * next step.
     */
    val showDownloadSummary: Boolean get() = downloadedCount > 0

    /**
     * Whether the grid is currently filtered down to nothing. Distinct from an empty library: the
     * content exists and the student's own chip is hiding it, so the way out is to change the chip,
     * not to download or retry anything.
     */
    val showFilteredEmpty: Boolean
        get() = !isLoading && items.isNotEmpty() && visibleItems.isEmpty()
    /**
     * FR-022: whether the Continue Learning entry's matn is downloaded, joining [continueLearning]
     * with [availability] — drives [com.giraffe.matn.presentation.common.ContinueLearningCard]'s
     * re-download offer. `true` when there is no entry (nothing to gate).
     */
    val isContinueLearningContentInstalled: Boolean
        get() = continueLearning?.let { entry ->
            availability[entry.matnId] is ContentAvailability.Downloaded
        } ?: true

    // ---------------------------------------------------------------- FR-044's three empty states
    //
    // Phase 13 replaced a single `isEmpty` flag with three. That flag conflated situations the spec
    // requires the student to be able to tell apart: "we could not reach the catalog", "the teacher
    // has published nothing", and "you have not downloaded anything yet" look identical as an empty
    // grid, but they call for completely different actions — retry, wait, or go download something.

    /**
     * Never reached the catalog. The only state that may show the connect-to-browse prompt — and
     * the honest first-launch state now that nothing ships in the binary (Principle VI, amended).
     */
    val showConnectPrompt: Boolean
        get() = !isLoading && items.isEmpty() && syncState?.hasEverSynced != true

    /** Reached the catalog; the teacher has published nothing yet. Not a failure. */
    val showEmptyCatalog: Boolean
        get() = !isLoading && items.isEmpty() && syncState?.hasEverSynced == true

    /** The catalog has متون, but none of them are on the device yet. */
    val showNothingDownloaded: Boolean
        get() = !isLoading && items.isNotEmpty() &&
            items.none { availability[it.matn.id] is ContentAvailability.Downloaded }

    /**
     * A sync failed while a usable catalog is already on screen (FR-007). Rendered as a
     * non-blocking, retryable notice — never as an emptied library.
     */
    val showSyncFailedNotice: Boolean
        get() = syncState?.lastAttemptFailed == true && items.isNotEmpty()
}
