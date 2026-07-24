package com.giraffe.matn.domain.session

import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.model.ResumeTarget
import com.giraffe.matn.domain.model.SavedMatnSession

/**
 * Pure resolver for "what should tapping Continue Learning do?" Implements rules R1–R9 in
 * data-model.md §3. No coroutines, no I/O, no repository — the table-test target, mirroring Phase
 * 3's [com.giraffe.matn.domain.repetition.RepetitionPlanner] (constitution Principle V).
 *
 * Returns [ResumeTarget.None] for the unhonourable cases (no session, no verses, matn gone — R1/R2/R6)
 * and a [ResumeTarget.Resolved] otherwise, clearing the loop (but keeping counters) when a loop
 * endpoint is missing (R7) or the resolved verse falls outside its own range (R8). A substituted
 * verse (saved verse gone) resolves at the nearest surviving neighbour with `positionMs = 0`
 * (R4/R5). [RepeatCount.Unlimited] and the rest of `settings` round-trip untouched.
 */
object ResumeTargetResolver {

    fun resolve(session: SavedMatnSession?, orderedVerses: List<VerseRef>): ResumeTarget {
        // R1/R6 — nothing to resume.
        if (session == null || orderedVerses.isEmpty()) return ResumeTarget.None

        // R3/R4/R5 — locate the resume vertex.
        val exact = orderedVerses.firstOrNull { it.id == session.lastVerseId }
        val verse: VerseRef
        val positionMs: Long
        val substituted: Boolean
        if (exact != null) {
            verse = exact
            positionMs = session.positionMs
            substituted = false
        } else {
            // Nearest preceding by display number; fall forward if none precede.
            val preceding = orderedVerses
                .filter { it.displayNumber < session.lastVerseDisplayNumber }
                .maxByOrNull { it.displayNumber }
            val resolved = preceding
                ?: orderedVerses
                    .filter { it.displayNumber > session.lastVerseDisplayNumber }
                    .minByOrNull { it.displayNumber }
                ?: return ResumeTarget.None // R6: matn emptied of comparable verses (defensive).
            verse = resolved
            positionMs = 0L
            substituted = true
        }

        // R7/R8 — loop validation. Prefer clearing the loop over discarding the session;
        // counters always survive.
        var settings = session.settings
        val loop = settings.loopRange
        if (loop != null) {
            val startPresent = orderedVerses.any { it.id == loop.startVerseId }
            val endPresent = orderedVerses.any { it.id == loop.endVerseId }
            if (!startPresent || !endPresent) {
                settings = settings.copy(loopRange = null) // R7
            } else {
                val start = orderedVerses.first { it.id == loop.startVerseId }.displayNumber
                val end = orderedVerses.first { it.id == loop.endVerseId }.displayNumber
                val (lo, hi) = if (start <= end) start to end else end to start
                if (verse.displayNumber < lo || verse.displayNumber > hi) {
                    settings = settings.copy(loopRange = null) // R8
                }
            }
        }

        return ResumeTarget.Resolved(
            matnId = session.matnId,
            verseId = verse.id,
            positionMs = positionMs,
            settings = settings,
            substituted = substituted,
        )
    }
}