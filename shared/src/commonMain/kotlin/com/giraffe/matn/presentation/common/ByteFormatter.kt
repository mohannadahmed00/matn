package com.giraffe.matn.presentation.common

/**
 * Pure byte formatter — converts a byte count to a human-readable size string (T032, FR-031).
 *
 * Uses **SI / decimal units** (1000-based), so 2_400_000 = "2.4 MB" and 1_000_000 = "1.0 MB",
 * matching the example in the task and how the OS surfaces app-storage sizes to users.
 *
 * - `B` below 1 KB (e.g. `0 B`, `296 B`, `999 B`)
 * - one decimal place above KB / MB / GB (e.g. `1.0 KB`, `2.4 MB`, `3.5 GB`)
 *
 * **Bidi-safe.** The result is wrapped in a [ltrIsolated] LTR isolate, because a size is a number
 * followed by a Latin unit and must keep that order in both interface languages. Without it the
 * space between value and unit is a neutral character, and an Arabic (RTL) paragraph resolves it
 * right-to-left — rendering `765.8 KB` as `KB 765.8`. See [BidiText.kt] for the full rationale.
 *
 * The isolate characters are zero-width and ignored by screen readers, so the result stays usable
 * as an accessibility label.
 *
 * No platform APIs (Constitution Principle IV); no locale-sensitive `String.format`. Negative
 * inputs are clamped to zero.
 */
fun formatBytes(bytes: Long): String = ltrIsolated(formatBytesRaw(bytes))

/**
 * The unwrapped size string, without bidi isolation — for tests and for callers that compose a
 * larger run and isolate it themselves. Prefer [formatBytes] anywhere the result reaches the screen.
 */
internal fun formatBytesRaw(bytes: Long): String {
    val clamped = if (bytes < 0L) 0L else bytes
    if (clamped < 1_000L) return "$clamped B"
    val kb = clamped.toDouble() / 1_000.0
    if (kb < 1_000.0) return "${oneDecimal(kb)} KB"
    val mb = kb / 1_000.0
    if (mb < 1_000.0) return "${oneDecimal(mb)} MB"
    val gb = mb / 1_000.0
    return "${oneDecimal(gb)} GB"
}

/** Format [value] to exactly one decimal place with a deterministic dot decimal separator. */
private fun oneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundHalfUpToLong()
    val whole = tenths / 10
    val fraction = tenths % 10
    return "$whole.$fraction"
}

/** Half-up round to a non-negative Long; safe for values up to ~9.2 × 10^17. */
private fun Double.roundHalfUpToLong(): Long {
    if (this < 0.0) return 0L
    val floor = kotlin.math.floor(this)
    val diff = this - floor
    return if (diff.compareTo(0.5) >= 0) (floor + 1.0).toLong() else floor.toLong()
}