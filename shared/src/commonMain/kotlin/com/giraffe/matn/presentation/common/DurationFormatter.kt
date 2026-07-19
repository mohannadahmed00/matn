package com.giraffe.matn.presentation.common

/**
 * Pure duration formatter — converts milliseconds to a human-readable time string:
 *  * `m:ss` for durations under an hour (e.g. `0:08`, `9:42`)
 *  * `h:mm:ss` for durations ≥ 1 hour (e.g. `1:05:09`)
 *
 * No platform APIs (Constitution Principle IV). Negative inputs are clamped to zero.
 */
fun formatDuration(ms: Long): String {
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