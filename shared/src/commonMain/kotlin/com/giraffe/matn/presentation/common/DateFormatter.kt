package com.giraffe.matn.presentation.common

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure calendar-date formatter for timestamps the student is shown — currently the Saved tab's
 * "confirmed on" line.
 *
 * Renders the ISO extended form (`2026-08-12`) rather than a month name. That is a deliberate
 * bilingual choice, not a shortcut: a localized month name would need a name table per locale and
 * would still have to agree with whichever of Arabic/English the interface is running, whereas the
 * all-numeric form reads identically in both and cannot be mistaken for a different date.
 *
 * **Bidi-safe.** Every character is a digit or a hyphen — directionally weak or neutral — so
 * without an isolate the run adopts the paragraph direction and an RTL interface renders
 * `2026-08-12` as `12-08-2026`, silently swapping day and year. See [BidiText.kt].
 *
 * No platform APIs (Constitution Principle IV): the conversion goes through kotlinx-datetime, the
 * same dependency the repositories use for local epoch-days.
 */
fun formatDate(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    ltrIsolated(formatDateRaw(epochMillis, timeZone))

/** The unwrapped date string, without bidi isolation — for tests and for callers that compose a
 *  larger run and isolate it themselves. Prefer [formatDate] anywhere the result reaches the screen. */
internal fun formatDateRaw(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(timeZone)
        .date
        .toString()
