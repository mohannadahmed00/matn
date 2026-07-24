package com.giraffe.matn.domain

import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.decodeRepeatCount
import com.giraffe.matn.domain.model.encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepeatCountCodecTest {

    @Test
    fun unlimited_round_trips_as_the_unlimited_token() {
        assertEquals("UNLIMITED", RepeatCount.Unlimited.encode())
        assertEquals(RepeatCount.Unlimited, decodeRepeatCount("UNLIMITED"))
    }

    @Test
    fun finite_value_round_trips() {
        RepeatCount.of(7).also { assertEquals("7", it.encode()) }
        assertEquals(RepeatCount.of(7), decodeRepeatCount("7"))
    }

    @Test
    fun null_input_falls_back_to_one() {
        assertEquals(RepeatCount.ONE, decodeRepeatCount(null))
    }

    @Test
    fun unparseable_input_falls_back_to_one() {
        assertEquals(RepeatCount.ONE, decodeRepeatCount("garbage"))
    }

    @Test
    fun zero_input_is_clamped_to_finite_one() {
        assertEquals(RepeatCount.of(1), decodeRepeatCount("0"))
    }

    @Test
    fun over_max_input_is_clamped_to_finite_max() {
        assertEquals(RepeatCount.of(99), decodeRepeatCount("999"))
    }

    @Test
    fun fr_007_guard_unlimited_is_distinct_from_finite_one() {
        // The single invariant that must never regress: "UNLIMITED" survives as Unlimited,
        // never degrading to Finite(1) — which would silently turn ∞ drills into one-shot plays.
        assertFalse { decodeRepeatCount("UNLIMITED") == RepeatCount.of(1) }
        assertTrue { decodeRepeatCount("UNLIMITED") is RepeatCount.Unlimited }
    }
}