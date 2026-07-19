package com.giraffe.matn.domain.audio

/**
 * Screen wake-lock edge (D10 / FR-017). Android sets/clears
 * `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on the current window; iOS sets
 * `UIApplication.shared.isIdleTimerDisabled`. Both are one-line platform calls with no logic,
 * so the edge stays thin (Principle IV).
 *
 * Plain `commonMain` interface injected via `initMatnKoin(driverFactory, audioEngine, wakeLock)`
 * — no `expect`/`actual`. [NoOpWakeLock] is the default binding for US1/US2 until US3 supplies
 * the real platform implementations, so the app compiles and runs without any platform edge.
 */
interface WakeLock {
    fun acquire()
    fun release()
}

/** No-op default binding (keeps US1/US2 compiling and running without platform wake locks). */
object NoOpWakeLock : WakeLock {
    override fun acquire() {}
    override fun release() {}
}