package com.giraffe.matn.domain.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Platform seam over the OS "reduce motion" accessibility setting (research D7, contract
 * adaptive-motion §B2). Implemented by `AndroidMotionPreferences` and `IosMotionPreferences`;
 * faked in tests.
 */
interface MotionPreferences {
    /** Emits the current reduce-motion flag and re-emits when the OS setting changes. */
    fun observeReduceMotion(): Flow<Boolean>
}