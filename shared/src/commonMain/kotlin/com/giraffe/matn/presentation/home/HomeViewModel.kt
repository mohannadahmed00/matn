package com.giraffe.matn.presentation.home

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.ContinueLearningEntry
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
) : BaseViewModel<HomeUiState>(HomeUiState()) {

    private val _navigation = Channel<String>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    init {
        observeLibrary
            .invoke(Unit)
            .onEach { items ->
                setState {
                    it.copy(
                        isLoading = false,
                        items = items,
                        isEmpty = items.isEmpty(),
                        error = null,
                    )
                }
            }
            .launchIn(viewModelScope)

        observeContinueLearning
            .invoke(Unit)
            .onEach { entry ->
                setState { it.copy(continueLearning = entry) }
            }
            .launchIn(viewModelScope)
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