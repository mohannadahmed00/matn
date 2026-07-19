package com.giraffe.matn.audio

import android.app.Activity
import android.view.WindowManager
import com.giraffe.matn.domain.audio.WakeLock

/**
 * Android [WakeLock] (D10 / FR-017) — toggles `FLAG_KEEP_SCREEN_ON` on the provided [Activity]'s
 * window. Keeps the screen awake while playing in the foreground; restoring normal timeout on
 * pause/stop/end (SC-008). A screen wake lock (not a `PowerManager` partial lock) per the
 * decision: background audio is already kept alive by the foreground service, the requirement is
 * the screen staying on for hands-free reading.
 *
 * Acquire/release are no-op until an Activity is attached; safe to call before [attach]. The
 * Activity reference is cleared on [detach] so the controller/session never retains it past the
 * Activity lifetime (Principle: no leak, T043).
 */
class AndroidWakeLock : WakeLock {
    @Volatile
    private var activity: Activity? = null

    fun attach(activity: Activity) {
        this.activity = activity
        // If we were acquired before the Activity attached (rare), apply the flag now.
        if (holdRequested) applyFlag(true)
    }

    fun detach() {
        activity?.let { try { it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Throwable) {} }
        activity = null
    }

    @Volatile
    private var holdRequested: Boolean = false

    override fun acquire() {
        holdRequested = true
        applyFlag(true)
    }

    override fun release() {
        holdRequested = false
        applyFlag(false)
    }

    private fun applyFlag(on: Boolean) {
        val a = activity ?: return
        try {
            if (on) {
                a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (_: Throwable) {
            // Window may not be ready yet; the holdRequested flag reapplies on attach.
        }
    }
}