package com.giraffe.matn.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 9 required vectors from contracts/normalization-contract.md § Required test vectors
 * (specs/006-search-bookmarks-notes). Matching predicate under test:
 * `normalize(text).contains(normalize(query))`.
 */
class ArabicNormalizerTest {

    @Test
    fun `vector 1 - alef fold plus diacritic strip`() {
        assertMatches(query = "الاسلام", text = "الإِسْلَام")
    }

    @Test
    fun `vector 2 - fold applies to query side too`() {
        assertMatches(query = "الإسلام", text = "الاسلام")
    }

    @Test
    fun `vector 3 - ta-marbuta fold plus shadda-damma strip`() {
        assertMatches(query = "فتوه", text = "فُتُوَّة")
    }

    @Test
    fun `vector 4 - ya fold`() {
        assertMatches(query = "على", text = "علي")
    }

    @Test
    fun `vector 5 - digit unification`() {
        assertMatches(query = "٥", text = "5")
    }

    @Test
    fun `vector 6 - hamza forms are NOT folded - bounded scope`() {
        assertFalse(
            ArabicNormalizer.normalize("مؤمن مءمن").let { normalizedText ->
                // Build both sides independently to test the negative match, not a coincidental
                // substring of the combined sentence.
                ArabicNormalizer.normalize("مءمن").contains(ArabicNormalizer.normalize("مؤمن"))
            },
            "hamza-on-waw (مؤمن) must NOT match hamza-on-alef-seat (مءمن) — out of scope per spec",
        )
    }

    @Test
    fun `vector 7 - whitespace collapse and trim`() {
        assertMatches(query = "  باب   الطهارة ", text = "باب الطهارة")
    }

    @Test
    fun `vector 8 - blank normalizes to empty string`() {
        assertEquals("", ArabicNormalizer.normalize(""))
        assertEquals("", ArabicNormalizer.normalize("   "))
    }

    @Test
    fun `vector 9 - idempotence over all vectors`() {
        val samples = listOf(
            "الاسلام", "الإِسْلَام", "الإسلام", "فتوه", "فُتُوَّة", "على", "علي",
            "٥", "5", "مؤمن", "مءمن", "  باب   الطهارة ", "باب الطهارة", "", "   ",
        )
        for (s in samples) {
            val once = ArabicNormalizer.normalize(s)
            val twice = ArabicNormalizer.normalize(once)
            assertEquals(once, twice, "normalize should be idempotent for input: $s")
        }
    }

    private fun assertMatches(query: String, text: String) {
        assertTrue(
            ArabicNormalizer.normalize(text).contains(ArabicNormalizer.normalize(query)),
            "expected normalize(\"$text\") to contain normalize(\"$query\")",
        )
    }
}
