package com.giraffe.matn.presentation.common

/**
 * Pure duration formatter — converts milliseconds to a human-readable time string:
 *  * `m:ss` for durations under an hour (e.g. `0:08`, `9:42`)
 *  * `h:mm:ss` for durations ≥ 1 hour (e.g. `1:05:09`)
 *
 * **Bidi-safe.** The result is wrapped in a [ltrIsolated] LTR isolate. A duration is all digits and
 * colons — every character is directionally weak or neutral, so with no isolate the run has nothing
 * to anchor it and simply adopts the paragraph direction, reversing `0:31` into `31:0` and tearing
 * apart composites like `4 verses · 0:31`. See [BidiText.kt] for the full rationale.
 *
 * The isolate characters are zero-width and ignored by screen readers, so the result stays usable
 * as an accessibility label.
 *
 * No platform APIs (Constitution Principle IV). Negative inputs are clamped to zero.
 */
fun formatDuration(ms: Long): String = ltrIsolated(formatDurationRaw(ms))

/**
 * The unwrapped duration string, without bidi isolation — for tests and for callers that compose a
 * larger run and isolate it themselves. Prefer [formatDuration] anywhere the result reaches the
 * screen.
 */
internal fun formatDurationRaw(ms: Long): String {
    val totalSeconds = if (ms < 0L) 0L else ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:$mm:$ss"
    } else {
        "$minutes:$ss"
    }
}