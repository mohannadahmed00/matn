package com.giraffe.matn.playback

import com.giraffe.matn.domain.audio.WakeLock

/**
 * In-memory [WakeLock] for `commonTest` (Principle V). Records acquire/release recount and
 * the current held state so `PlaybackControllerTest` can assert wake-lock gating (FR-017).
 */
class FakeWakeLock : WakeLock {
    var acquired: Boolean = false
        private set
    var acquireCount: Int = 0
        private set
    var releaseCount: Int = 0
        private set

    override fun acquire() {
        acquired = true
        acquireCount++
    }

    override fun release() {
        acquired = false
        releaseCount++
    }
}