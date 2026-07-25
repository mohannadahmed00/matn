package com.giraffe.matn.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T032 — `formatBytes` coverage: zero, sub-KB, the 1 KiB boundary, a typical MB, and a multi-GB
 * value (FR-031 — every numeral must round to one decimal place above KB).
 */
class ByteFormatterTest {

    @Test
    fun `zero bytes renders with the B unit`() {
        assertEquals("0 B", formatBytes(0L))
    }

    @Test
    fun `sub-KB value stays in B without a decimal`() {
        assertEquals("999 B", formatBytes(999L))
    }

    @Test
    fun `the 1 KiB boundary formats as one decimal KB`() {
        assertEquals("1.0 KB", formatBytes(1_024L))
    }

    @Test
    fun `a typical MB value rounds to one decimal`() {
        assertEquals("2.4 MB", formatBytes(2_400_000L))
    }

    @Test
    fun `a multi-GB value rounds to one decimal GB`() {
        assertEquals("3.5 GB", formatBytes(3_500_000_000L))
    }

    @Test
    fun `negative input is clamped to zero`() {
        assertEquals("0 B", formatBytes(-1L))
    }
}