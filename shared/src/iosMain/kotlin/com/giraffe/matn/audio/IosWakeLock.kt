package com.giraffe.matn.audio

import com.giraffe.matn.domain.audio.WakeLock
import platform.UIKit.UIApplication

/**
 * iOS [WakeLock] (D10 / FR-017) — toggles `UIApplication.sharedApplication.idleTimerDisabled`
 * while playing so the screen stays awake for hands-free reading; restored on pause/stop/end
 * (SC-008). Must be touched on the main thread; the controller only calls acquire/release on
 * state transitions that already run on the main dispatcher.
 */
class IosWakeLock : WakeLock {
    override fun acquire() {
        runCatching { UIApplication.sharedApplication.idleTimerDisabled = true }
    }

    override fun release() {
        runCatching { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}