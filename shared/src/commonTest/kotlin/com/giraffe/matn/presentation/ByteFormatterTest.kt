package com.giraffe.matn.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T032 — `formatBytes` coverage: zero, sub-KB, the 1 KiB boundary, a typical MB, and a multi-GB
 * value (FR-031 — every numeral must round to one decimal place above KB).
 *
 * The value assertions run against [formatBytesRaw], so they express the *numeric* contract without
 * the bidi isolate wrapper obscuring it. The wrapper is asserted separately, once, in
 * [formatted sizes are wrapped in an LTR isolate] — it is a display concern, not a formatting one.
 */
class ByteFormatterTest {

    @Test
    fun `zero bytes renders with the B unit`() {
        assertEquals("0 B", formatBytesRaw(0L))
    }

    @Test
    fun `sub-KB value stays in B without a decimal`() {
        assertEquals("999 B", formatBytesRaw(999L))
    }

    @Test
    fun `the 1 KiB boundary formats as one decimal KB`() {
        assertEquals("1.0 KB", formatBytesRaw(1_024L))
    }

    @Test
    fun `a typical MB value rounds to one decimal`() {
        assertEquals("2.4 MB", formatBytesRaw(2_400_000L))
    }

    @Test
    fun `a multi-GB value rounds to one decimal GB`() {
        assertEquals("3.5 GB", formatBytesRaw(3_500_000_000L))
    }

    @Test
    fun `negative input is clamped to zero`() {
        assertEquals("0 B", formatBytesRaw(-1L))
    }

    /**
     * The regression this guards: an unisolated `"765.8 KB"` inside an Arabic paragraph renders as
     * `KB 765.8`, because the space between value and unit is a neutral character resolved against
     * the paragraph direction. The isolate pins the run left-to-right in both interface languages.
     */
    @Test
    fun `formatted sizes are wrapped in an LTR isolate`() {
        val formatted = formatBytes(765_800L)
        assertEquals(ltrIsolated("765.8 KB"), formatted)
        assertTrue(isIsolated(formatted), "formatBytes must isolate its result for RTL safety")
    }
}
