package com.giraffe.matn.preferences

import com.giraffe.matn.domain.preferences.MotionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake [MotionPreferences] for tests (T031). Backs [observeReduceMotion] with a
 * [MutableStateFlow] a test can drive directly, and exposes it via [state] for assertions.
 */
class FakeMotionPreferences(
    initialValue: Boolean = false,
) : MotionPreferences {
    private val _state = MutableStateFlow(initialValue)
    val state: StateFlow<Boolean> = _state.asStateFlow()

    fun set(value: Boolean) { _state.value = value }

    override fun observeReduceMotion(): kotlinx.coroutines.flow.Flow<Boolean> = state
}