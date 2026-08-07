package com.giraffe.matn.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for the bidi isolate helpers that keep mixed-direction strings readable in both
 * interface languages (see `BidiText.kt`).
 *
 * These tests assert the *characters emitted*, since that is the entire contract — the visual
 * effect is produced by the platform text engine applying UAX #9 to them, and cannot be observed
 * from a unit test.
 */
private const val LRI = '⁦'
private const val FSI = '⁨'
private const val PDI = '⁩'

class BidiTextTest {

    @Test
    fun `ltrIsolated brackets the run with LRI and PDI`() {
        assertEquals("${LRI}0:31$PDI", ltrIsolated("0:31"))
    }

    @Test
    fun `autoIsolated brackets the run with FSI and PDI`() {
        assertEquals("${FSI}4 verses$PDI", autoIsolated("4 verses"))
    }

    @Test
    fun `an empty run still produces a well-formed isolate`() {
        assertEquals("$LRI$PDI", ltrIsolated(""))
    }

    @Test
    fun `isolation is detected for both isolate kinds`() {
        assertTrue(isIsolated(ltrIsolated("40%")))
        assertTrue(isIsolated(autoIsolated("الأجرومية")))
    }

    @Test
    fun `plain text is not reported as isolated`() {
        assertFalse(isIsolated("40%"))
        assertFalse(isIsolated(""))
    }

    /**
     * A run that merely *contains* isolate characters is not itself isolated — the check looks at
     * the boundaries, so a composite built from isolated parts still gets wrapped by its caller.
     */
    @Test
    fun `a composite of isolated parts is not itself isolated`() {
        val composite = ltrIsolated("2.4 MB") + " / " + ltrIsolated("1.0 GB")
        assertFalse(isIsolated(composite))
        assertTrue(isIsolated(ltrIsolated(composite)))
    }
}
