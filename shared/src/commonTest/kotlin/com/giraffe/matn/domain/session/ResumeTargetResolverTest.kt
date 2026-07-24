package com.giraffe.matn.domain.session

import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.model.ResumeTarget
import com.giraffe.matn.domain.model.SavedMatnSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResumeTargetResolverTest {

    private fun verses(vararg ids: Pair<String, Int>): List<VerseRef> =
        ids.map { VerseRef(it.first, it.second) }

    private fun session(
        matnId: String = "m1",
        lastVerseId: String,
        lastDisplayNumber: Int,
        settings: RepetitionSettings = RepetitionSettings(),
        positionMs: Long = 1_000L,
    ) = SavedMatnSession(matnId, lastVerseId, lastDisplayNumber, positionMs, settings)

    // R1 — null session.
    @Test
    fun `null session resolves to None`() {
        assertIs<ResumeTarget.None>(ResumeTargetResolver.resolve(null, verses("v1" to 1)))
    }

    // R6 — empty verses.
    @Test
    fun `empty verses resolves to None`() {
        assertIs<ResumeTarget.None>(ResumeTargetResolver.resolve(session(lastVerseId = "v1", lastDisplayNumber = 1), emptyList()))
    }

    // R3 — verse present, exact position retained.
    @Test
    fun `verse present resolves at exact positionMs and is not substituted`() {
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "v3", lastDisplayNumber = 3, positionMs = 4_500L),
            verses("v1" to 1, "v2" to 2, "v3" to 3, "v4" to 4),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertEquals("v3", r.verseId)
        assertEquals(4_500L, r.positionMs)
        assertFalse(r.substituted)
    }

    // R4 — verse gone, nearest preceding.
    @Test
    fun `verse gone with predecessor resolves at predecessor with position zero and substituted`() {
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "gone", lastDisplayNumber = 3, positionMs = 9_999L),
            verses("v1" to 1, "v2" to 2, "v4" to 4, "v5" to 5),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertEquals("v2", r.verseId)
        assertEquals(0L, r.positionMs)
        assertTrue(r.substituted)
    }

    // R5 — verse gone, first verse deleted (falls forward to nearest following).
    @Test
    fun `verse gone with no predecessor falls forward to nearest following`() {
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "gone", lastDisplayNumber = 1, positionMs = 1L),
            verses("v2" to 2, "v3" to 3, "v4" to 4),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertEquals("v2", r.verseId)
        assertEquals(0L, r.positionMs)
        assertTrue(r.substituted)
    }

    // R6 variant — matn emptied (every verse removed).
    @Test
    fun `matn emptied resolves to None`() {
        assertIs<ResumeTarget.None>(
            ResumeTargetResolver.resolve(session(lastVerseId = "v1", lastDisplayNumber = 1), emptyList())
        )
    }

    // R7 — loop endpoint gone: loop cleared, counters retained.
    @Test
    fun `loop endpoint gone clears loop but keeps counters`() {
        val settings = RepetitionSettings(
            verseRepeat = RepeatCount.of(7),
            matnRepeat = RepeatCount.of(2),
            loopRange = LoopRange("v1", "gone-end"),
        )
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "v2", lastDisplayNumber = 2, settings = settings),
            verses("v1" to 1, "v2" to 2, "v3" to 3),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertNull(r.settings.loopRange)
        assertEquals(RepeatCount.of(7), r.settings.verseRepeat)
        assertEquals(RepeatCount.of(2), r.settings.matnRepeat)
    }

    // R8 — resolved verse outside its own loop range: loop cleared.
    @Test
    fun `resolved verse outside its loop clears the loop`() {
        val settings = RepetitionSettings(
            verseRepeat = RepeatCount.of(3),
            loopRange = LoopRange("v1", "v3"),
        )
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "v5", lastDisplayNumber = 5, settings = settings),
            verses("v1" to 1, "v2" to 2, "v3" to 3, "v4" to 4, "v5" to 5),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertEquals("v5", r.verseId)
        assertNull(r.settings.loopRange)
        assertEquals(RepeatCount.of(3), r.settings.verseRepeat)
    }

    // R8 inverse — resolved verse inside its loop: loop retained.
    @Test
    fun `resolved verse inside its loop keeps the loop`() {
        val loop = LoopRange("v1", "v4")
        val settings = RepetitionSettings(verseRepeat = RepeatCount.of(3), loopRange = loop)
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "v2", lastDisplayNumber = 2, settings = settings),
            verses("v1" to 1, "v2" to 2, "v3" to 3, "v4" to 4),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertEquals(loop, r.settings.loopRange)
    }

    // Loop range stored in reverse order (start > end) is still honoured.
    @Test
    fun `loop stored with start greater than end is normalized for the containment check`() {
        val loop = LoopRange("v4", "v1") // reversed
        val settings = RepetitionSettings(loopRange = loop)
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "v2", lastDisplayNumber = 2, settings = settings),
            verses("v1" to 1, "v2" to 2, "v3" to 3, "v4" to 4),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertEquals(loop, r.settings.loopRange) // retained — v2 is inside [1..4]
    }

    // Unlimited counters round-trip through the resolver untouched (R9 sibling guard).
    @Test
    fun `unlimited counters are preserved by resolution`() {
        val settings = RepetitionSettings(
            verseRepeat = RepeatCount.Unlimited,
            matnRepeat = RepeatCount.Unlimited,
        )
        val r = ResumeTargetResolver.resolve(
            session(lastVerseId = "v1", lastDisplayNumber = 1, settings = settings),
            verses("v1" to 1, "v2" to 2),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertTrue(r.settings.verseRepeat is RepeatCount.Unlimited)
        assertTrue(r.settings.matnRepeat is RepeatCount.Unlimited)
    }

// Substituted fallback also clears an out-of-range loop (R4 + R8 combined).
    @Test
    fun `substituted verse outside its loop clears the loop`() {
        // Saved verse gone (id "gone", displayNumber 1). Surviving verses v1(dn1), v2(dn2).
        // No preceding (dn<1) -> falls forward to v2 (dn2, smallest dn>1). Loop is v1..v1, so v2
        // is outside [1..1] -> R8 clears the loop; R4 supplies substituted=true, positionMs=0.
        val r = ResumeTargetResolver.resolve(
            session(
                lastVerseId = "gone",
                lastDisplayNumber = 1,
                settings = RepetitionSettings(loopRange = LoopRange("v1", "v1")),
            ),
            verses("v1" to 1, "v2" to 2),
        )
        assertIs<ResumeTarget.Resolved>(r)
        assertEquals("v2", r.verseId)
        assertTrue(r.substituted)
        assertEquals(0L, r.positionMs)
        assertNull(r.settings.loopRange, "resolved verse outside its own loop -> loop cleared")
    }
}