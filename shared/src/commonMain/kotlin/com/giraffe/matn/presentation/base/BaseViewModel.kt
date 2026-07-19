package com.giraffe.matn.presentation.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared base for every screen's ViewModel (Constitution Principle II + Principle III).
 *
 * Exposes exactly one immutable observable UI-state [S] via [state] (a `StateFlow`); subclass
 * code mutates the state through [setState]. Composables are pure functions of this state and
 * emit intents — they do not call use cases/repositories directly (Principle I/II).
 *
 * Does not reference any Compose, Android `Context`, `View`, or platform UI type. Lives once
 * in `commonMain` (Principle IV). Uses [viewModelScope]; sub-classes never leak their own scope.
 */
abstract class BaseViewModel<S>(initial: S) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    protected val stateValue: S get() = _state.value

    /**
     * The single way to mutate UI state. The [reduce] lambda is called inline; use the
     * transparent mutator API of an immutable `data class` state (copy) inside the reduce.
     */
    protected fun setState(reduce: (S) -> S) {
        _state.value = reduce(_state.value)
    }

    /** Shared collection helper: owns cancellation via [viewModelScope] (Constitution III). */
    protected fun <T> Flow<T>.collectInto(onEach: (T) -> Unit) {
        viewModelScope.launch {
            collect { onEach(it) }
        }
    }

    /**
     * Convenience for one-shot use cases that return a [Resource]: runs the call, then applies
     * [onSuccess] only on `Success`, otherwise publishes [error] via [onError]. Loading state
     * is the subclass's responsibility (typically toggled before the call).
     * */
    protected fun <P, R> runUseCase(
        useCase: com.giraffe.matn.core.usecase.UseCase<P, R>,
        params: P,
        onSuccess: (R) -> Unit,
        onError: (AppError) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = useCase.invoke(params)) {
                is Resource.Success -> onSuccess(result.data)
                is Resource.Failure -> onError(result.error)
            }
        }
    }
}