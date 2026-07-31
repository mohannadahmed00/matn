package com.giraffe.matn.domain.audio

/** `split-contract.md` §2 — one case per rule R1–R7, each naming the verse(s) or stretch it's
 * about. R7 is the only warning; everything else refuses the split. */
sealed interface SplitProblem {
    data class MissingRange(val verseId: String) : SplitProblem
    data class InvertedRange(val verseId: String) : SplitProblem
    data class TooShort(val verseId: String) : SplitProblem
    data class OutOfBounds(val verseId: String, val sourceDurationMs: Long) : SplitProblem
    data class Overlap(val verseIdA: String, val verseIdB: String) : SplitProblem
    data class RangeOutOfScope(val verseId: String) : SplitProblem
    data class UncoveredStretch(val startMs: Long, val endMs: Long) : SplitProblem
}

data class SplitReport(val blocking: List<SplitProblem>, val warnings: List<SplitProblem>)

/** Pure function of [SplitPlan] — no I/O, no decoder — every rule is covered in `commonTest` by
 * constructing plans directly (`split-contract.md` §2). */
object SplitPlanValidator {
    private const val MIN_RANGE_MS = 300L
    private const val MIN_UNCOVERED_GAP_MS = 1000L

    fun validate(plan: SplitPlan): SplitReport {
        val blocking = mutableListOf<SplitProblem>()
        val warnings = mutableListOf<SplitProblem>()

        // R6 RangeOutOfScope
        plan.ranges.forEach { range ->
            if (range.verseId !in plan.scopeVerseIds) blocking.add(SplitProblem.RangeOutOfScope(range.verseId))
        }

        // R1 MissingRange
        val rangedVerseIds = plan.ranges.map { it.verseId }.toSet()
        plan.scopeVerseIds.forEach { verseId ->
            if (verseId !in rangedVerseIds) blocking.add(SplitProblem.MissingRange(verseId))
        }

        // R2 InvertedRange, R3 TooShort, R4 OutOfBounds
        plan.ranges.forEach { range ->
            if (range.endMs <= range.startMs) {
                blocking.add(SplitProblem.InvertedRange(range.verseId))
            } else if (range.endMs - range.startMs < MIN_RANGE_MS) {
                blocking.add(SplitProblem.TooShort(range.verseId))
            }
            if (range.startMs < 0 || range.endMs > plan.source.durationMs) {
                blocking.add(SplitProblem.OutOfBounds(range.verseId, plan.source.durationMs))
            }
        }

        // R5 Overlap — every pair, since a sort-adjacent scan can miss a range nested inside another.
        for (i in plan.ranges.indices) {
            for (j in i + 1 until plan.ranges.size) {
                val a = plan.ranges[i]
                val b = plan.ranges[j]
                if (a.startMs < b.endMs && b.startMs < a.endMs) blocking.add(SplitProblem.Overlap(a.verseId, b.verseId))
            }
        }

        // R7 UncoveredStretch — warning only; silence between verses is normal (FR-016).
        val wellFormed = plan.ranges
            .filter { it.endMs > it.startMs && it.startMs >= 0 && it.endMs <= plan.source.durationMs }
            .sortedBy { it.startMs }
        var cursor = 0L
        wellFormed.forEach { range ->
            if (range.startMs - cursor >= MIN_UNCOVERED_GAP_MS) {
                warnings.add(SplitProblem.UncoveredStretch(cursor, range.startMs))
            }
            cursor = maxOf(cursor, range.endMs)
        }
        if (plan.source.durationMs - cursor >= MIN_UNCOVERED_GAP_MS) {
            warnings.add(SplitProblem.UncoveredStretch(cursor, plan.source.durationMs))
        }

        return SplitReport(blocking = blocking, warnings = warnings)
    }
}
