package com.giraffe.matn.audio

import com.giraffe.matn.domain.audio.AudioProfile
import com.giraffe.matn.domain.audio.SourceRecording
import com.giraffe.matn.domain.audio.SplitPlan
import com.giraffe.matn.domain.audio.SplitPlanValidator
import com.giraffe.matn.domain.audio.SplitProblem
import com.giraffe.matn.domain.audio.VerseRange
import kotlin.test.Test
import kotlin.test.assertTrue

private fun source(durationMs: Long = 10_000) =
    SourceRecording(localPath = "/tmp/rec.mp3", sizeBytes = 1_000_000, durationMs = durationMs, profile = AudioProfile(44100, 1), frameCount = 100)

private fun plan(scope: List<String>, ranges: List<VerseRange>, durationMs: Long = 10_000) =
    SplitPlan(source = source(durationMs), scopeVerseIds = scope, ranges = ranges)

class SplitPlanValidatorTest {

    @Test
    fun `R1 MissingRange — a verse in scope has no range`() {
        val report = SplitPlanValidator.validate(plan(scope = listOf("v1", "v2"), ranges = listOf(VerseRange("v1", 0, 1000))))

        assertTrue(report.blocking.any { it is SplitProblem.MissingRange && it.verseId == "v2" })
    }

    @Test
    fun `R2 InvertedRange — endMs less than or equal startMs`() {
        val report = SplitPlanValidator.validate(plan(scope = listOf("v1"), ranges = listOf(VerseRange("v1", 1000, 500))))

        assertTrue(report.blocking.any { it is SplitProblem.InvertedRange && it.verseId == "v1" })
    }

    @Test
    fun `R3 TooShort — range under 300ms`() {
        val report = SplitPlanValidator.validate(plan(scope = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 200))))

        assertTrue(report.blocking.any { it is SplitProblem.TooShort && it.verseId == "v1" })
    }

    @Test
    fun `R4 OutOfBounds — endMs exceeds source duration`() {
        val report = SplitPlanValidator.validate(plan(scope = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 20_000)), durationMs = 10_000))

        assertTrue(report.blocking.any { it is SplitProblem.OutOfBounds && it.verseId == "v1" })
    }

    @Test
    fun `R5 Overlap — two ranges intersect`() {
        val report = SplitPlanValidator.validate(
            plan(scope = listOf("v1", "v2"), ranges = listOf(VerseRange("v1", 0, 2000), VerseRange("v2", 1000, 3000))),
        )

        assertTrue(report.blocking.any { it is SplitProblem.Overlap && it.verseIdA == "v1" && it.verseIdB == "v2" })
    }

    @Test
    fun `R6 RangeOutOfScope — a range names a verse not in scope`() {
        val report = SplitPlanValidator.validate(plan(scope = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 1000), VerseRange("v2", 1000, 2000))))

        assertTrue(report.blocking.any { it is SplitProblem.RangeOutOfScope && it.verseId == "v2" })
    }

    @Test
    fun `R7 UncoveredStretch — 1s or more of uncovered source is a warning - not blocking`() {
        val report = SplitPlanValidator.validate(
            plan(scope = listOf("v1", "v2"), ranges = listOf(VerseRange("v1", 0, 1000), VerseRange("v2", 5000, 6000)), durationMs = 10_000),
        )

        assertTrue(report.warnings.any { it is SplitProblem.UncoveredStretch })
        assertTrue(report.blocking.isEmpty())
    }

    @Test
    fun `a valid plan produces no blocking problems`() {
        val report = SplitPlanValidator.validate(
            plan(scope = listOf("v1", "v2"), ranges = listOf(VerseRange("v1", 0, 1000), VerseRange("v2", 1000, 2000)), durationMs = 2000),
        )

        assertTrue(report.blocking.isEmpty())
    }

    @Test
    fun `a plan whose only problem is R7 is still splittable`() {
        val report = SplitPlanValidator.validate(
            plan(scope = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 1000)), durationMs = 5000),
        )

        assertTrue(report.blocking.isEmpty())
        assertTrue(report.warnings.isNotEmpty())
    }
}
