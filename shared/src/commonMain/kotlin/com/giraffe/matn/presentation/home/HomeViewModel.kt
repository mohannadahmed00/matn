package com.giraffe.matn.presentation.home

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogSyncState
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.ResumeTarget
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.presentation.base.BaseViewModel
import com.giraffe.matn.playback.PlaybackController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the Home library grid state (data-model.md §4.1; Principle II). On init it runs **two
 * independent collectors**:
 *
 *  * The **library** collector maps each emission to [items] and flips [isLoading]/[isEmpty].
 *  * The **Continue Learning** collector maps its own emission to [HomeUiState.continueLearning].
 *
 * The Continue Learning collector MUST be its own `.onEach { }.launchIn(viewModelScope)` and MUST
 * NOT gate [isLoading] (SC-005/T043a): the library grid paints as soon as its first emission
 * arrives, whether or not the entry has resolved. `continueLearning` simply stays `null` until it
 * does, and `null` renders nothing at all (FR-015).
 *
 * Resume/dismss intents forward to thin use cases; the ViewModel owns no branching — the resume
 * resolution lives in `ResolveResumeTargetUseCase` + the pure `ResumeTargetResolver` (Principle II
 * + V). A one-shot [navigation] event carries the matn id to open so navigation stays out of the
 * ViewModel (Principle II — no androidx.navigation here).
 */
class HomeViewModel(
    observeLibrary: FlowUseCase<Unit, List<MatnSummary>>,
    observeContinueLearning: FlowUseCase<Unit, ContinueLearningEntry?> = NoContinueLearning,
    private val resolveResumeTarget: UseCase<String, ResumeTarget> = NoResumeTarget,
    private val dismissContinueLearning: UseCase<Unit, Unit> = NoOpDismiss,
    private val playbackController: PlaybackController? = null,
    private val settingsStore: RepetitionSettingsStore? = null,
    /** Phase 7 (US1 FR-006): per-matn progress for the library cards. Optional/defaulted so
     *  existing call sites and tests keep compiling. */
    private val observeLibraryProgress: FlowUseCase<Unit, List<MatnProgress>>? = null,
    /** Phase 7 (US2 FR-009/FR-012): today's practice count + goal for the completion ring. */
    private val observeDailyProgress: FlowUseCase<Unit, DailyProgress>? = null,
    /** Phase 8 (US1 FR-002): per-matn content-delivery availability for the library cards. */
    private val observeLibraryAvailability: FlowUseCase<Unit, Map<String, ContentAvailability>>? = null,
    /** Phase 13 (FR-005): the catalog — every published matn, downloaded or not. Supersedes
     *  [observeLibrary] for the grid when supplied. */
    private val observeCatalog: FlowUseCase<Unit, List<MatnSummary>>? = null,
    /** Phase 13 (FR-044): whether the catalog has ever synced and whether the last attempt failed. */
    private val observeCatalogSyncState: FlowUseCase<Unit, CatalogSyncState>? = null,
    /** Phase 13 (FR-006): reconciles against the source. `false` honours the staleness window. */
    private val syncCatalog: UseCase<Boolean, Unit>? = null,
    /** Phase 13 (FR-012): `(matnId, coverImageRef) -> bytes?`. Browse-path only; never throws. */
    private val loadCover: (suspend (Pair<String, String>) -> ByteArray?)? = null,
) : BaseViewModel<HomeUiState>(HomeUiState()) {

    private val _navigation = Channel<String>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    init {
        // Phase 13 (FR-005): the grid is the CATALOG — every published matn, downloaded or not —
        // not just what is on the device. `observeCatalog` supersedes `observeLibrary` when it is
        // supplied; the latter remains the fallback so existing tests and call sites keep working.
        (observeCatalog ?: observeLibrary)
            .invoke(Unit)
            .onEach { items ->
                setState {
                    it.copy(
                        isLoading = false,
                        items = items,
                        error = null,
                    )
                }
                prefetchCovers(items)
            }
            .launchIn(viewModelScope)

        // FR-006: sync when the library opens. Non-forced, so the 1-hour staleness window applies
        // and navigating in and out of the library costs nothing.
        refreshCatalog(force = false)

        // FR-044: collected independently of the sync itself, so the three empty states resolve
        // from stored truth even when a sync is still in flight or has failed.
        observeCatalogSyncState?.invoke(Unit)
            ?.onEach { sync -> setState { it.copy(syncState = sync) } }
            ?.launchIn(viewModelScope)

        observeContinueLearning
            .invoke(Unit)
            .onEach { entry ->
                setState { it.copy(continueLearning = entry) }
            }
            .launchIn(viewModelScope)

        // Phase 8 (FR-002, storage-ui-contract.md §5): its own collector, never gating
        // isLoading — a mid-flight availability change (backgrounding, eviction, install
        // completion) reaches the state object purely through Flow re-collection.
        observeLibraryAvailability?.invoke(Unit)?.collectInto { availabilityMap ->
            setState { it.copy(availability = availabilityMap) }
        }

        observeLibraryProgress?.invoke(Unit)
            ?.onEach { progressList ->
                setState { it.copy(progressByMatn = progressList.associate { p -> p.matnId to p.fraction }) }
            }
            ?.launchIn(viewModelScope)

        observeDailyProgress?.invoke(Unit)
            ?.onEach { progress ->
                setState {
                    it.copy(
                        dailyGoal = DailyGoalUiState(
                            practiced = progress.practicedToday,
                            goal = progress.goal,
                            fraction = progress.fraction,
                            isComplete = progress.isComplete,
                        ),
                    )
                }
            }
            ?.launchIn(viewModelScope)
    }

    /**
     * The student's explicit refresh (FR-006). Always syncs, ignoring the staleness window.
     */
    fun onRefreshClicked() = refreshCatalog(force = true)

    /**
     * Runs a sync without ever blocking the grid.
     *
     * A failure is deliberately swallowed here rather than routed to `error`: the repository leaves
     * the last successfully synced catalog completely intact (FR-007), and `syncState` already
     * carries the failure flag for the non-blocking notice. Surfacing it as a screen-level error
     * would empty a library that is still perfectly usable — the exact behaviour FR-007 forbids.
     */
    /**
     * Fetches any cover the grid does not already have (FR-012).
     *
     * This is the **only** place covers are fetched, and it sits on the browse path by
     * construction — the reading and playback paths never reach it. A cover leaking into those
     * would put a network call inside the offline guarantee (SC-004, Principle VI).
     *
     * Failures are invisible: `loadCover` returns null and the card keeps its placeholder.
     */
    private fun prefetchCovers(items: List<MatnSummary>) {
        val load = loadCover ?: return
        val needed = items.filter { it.matn.coverImageRef != null && it.matn.id !in state.value.covers }
        if (needed.isEmpty()) return
        viewModelScope.launch {
            needed.forEach { summary ->
                val bytes = load(summary.matn.id to summary.matn.coverImageRef.orEmpty()) ?: return@forEach
                setState { it.copy(covers = it.covers + (summary.matn.id to bytes)) }
            }
        }
    }

    private fun refreshCatalog(force: Boolean) {
        val sync = syncCatalog ?: return
        viewModelScope.launch {
            setState { it.copy(isSyncing = true) }
            sync(force)
            setState { it.copy(isSyncing = false) }
        }
    }

    /** Resume tap. Resolves the saved target, warms the settings store with the resolved drill
     *  (so `PlaybackController.startSession`'s synchronous `settingsStore.get` yields the saved
     *  drill rather than defaults — the single most likely bug in the phase), starts playback, and
     *  emits a navigation event. On `None` (or a resolution failure) it does nothing visible. */
    fun onResumeClicked() {
        val entry = stateValue.continueLearning ?: return
        val controller = playbackController ?: return
        viewModelScope.launch {
            val target = when (val r = resolveResumeTarget.invoke(entry.matnId)) {
                is Resource.Success -> r.data
                is Resource.Failure -> return@launch
            }
            if (target !is ResumeTarget.Resolved) return@launch
            // Warm the persistent store's cache + persist the resolved (possibly loop-healed)
            // settings before startSession reads them. put() updates the cache synchronously.
            settingsStore?.put(entry.matnId, target.settings)
            controller.playFromVerse(entry.matnId, target.verseId, target.positionMs)
            _navigation.send(entry.matnId)
        }
    }

    /** Dismiss tap. Clears the last-listened pointer and nothing else (FR-017a). */
    fun onDismissClicked() {
        viewModelScope.launch { dismissContinueLearning.invoke(Unit) }
    }
}

// The Phase-4 collaborators are defaulted so the Phase-1 constructor shape stays source-compatible
// (Rule 1: pre-existing tests construct HomeViewModel(observeLibrary) and must pass unchanged).
// Production wiring (MatnNavHost) always passes the real implementations.

private object NoContinueLearning : FlowUseCase<Unit, ContinueLearningEntry?> {
    override fun invoke(params: Unit): Flow<ContinueLearningEntry?> = flowOf(null)
}

private object NoResumeTarget : UseCase<String, ResumeTarget> {
    override suspend fun invoke(params: String): Resource<ResumeTarget> =
        Resource.Success(ResumeTarget.None)
}

private object NoOpDismiss : UseCase<Unit, Unit> {
    override suspend fun invoke(params: Unit): Resource<Unit> = Resource.Success(Unit)
}