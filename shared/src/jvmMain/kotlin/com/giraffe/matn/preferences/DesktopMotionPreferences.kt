package com.giraffe.matn.preferences

import com.giraffe.matn.domain.preferences.MotionPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop adapter for [MotionPreferences] (research D7, contract adaptive-motion §B2). The JDK
 * exposes no cross-platform "reduce motion" accessibility signal, so this reports a constant
 * `false` ("motion not reduced") — the same degrade-to-not-reduced posture `IosMotionPreferences`
 * takes for its not-yet-wired branches.
 */
class DesktopMotionPreferences : MotionPreferences {
    override fun observeReduceMotion(): Flow<Boolean> = flowOf(false)
}
