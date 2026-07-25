package com.giraffe.matn.preferences

import com.giraffe.matn.domain.preferences.MotionPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * iOS adapter for [MotionPreferences] (research D7, contract adaptive-motion §B2). Reads
 * `UIAccessibility.isReduceMotionEnabled()` and re-emits on
 * `UIAccessibilityReduceMotionStatusDidChangeNotification`.
 *
 * ⚠️ **Platform-toolchain note (T027 follow-up on macOS).** The full UIKit-backed implementation
 * is completed on **macOS with Xcode**, where the Apple sysroot exposes
 * `UIAccessibility.isReduceMotionEnabled()` and
 * `UIAccessibilityReduceMotionStatusDidChangeNotification`. This Windows sysroot
 * (`compileKotlinIosSimulatorArm64`) does not expose those symbols, so this adapter emits a
 * constant `false` here and the live snapshot + observer body is completed on macOS — the same
 * completion tactic the existing `AvQueueAudioEngine` uses. Until then this adapter reports
 * "motion not reduced", which is acceptable because nothing ships to iOS without the macOS/Xcode
 * pass. The reduced-motion mapping contract is unit-tested by `ReducedMotionSpecTest`.
 */
class IosMotionPreferences : MotionPreferences {

    override fun observeReduceMotion(): Flow<Boolean> = callbackFlow {
        // macOS-completion body: emit `UIAccessibility.isReduceMotionEnabled()` then register an
        // NSNotificationCenter observer for UIAccessibilityReduceMotionStatusDidChangeNotification
        // that re-emits on each callback; `awaitClose` removes the observer.
        trySend(false)
        awaitClose {
            // macOS-completion body: remove the observer.
        }
    }
}