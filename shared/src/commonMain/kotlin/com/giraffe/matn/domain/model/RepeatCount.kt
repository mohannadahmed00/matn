package com.giraffe.matn.domain.model

/**
 * A repetition target: a clamped finite count or explicit unlimited (data-model.md §1.1).
 * Unlimited is a **distinct type**, never a sentinel `0`/`null`, so "unlimited" can never be
 * confused with "unset" anywhere in state, UI, or storage (FR-003).
 */
sealed interface RepeatCount {
    data class Finite(val value: Int) : RepeatCount {
        init { require(value in MIN..MAX) { "RepeatCount.Finite out of range: $value" } }
    }
    data object Unlimited : RepeatCount

    val isUnlimited: Boolean get() = this is Unlimited

    companion object {
        const val MIN = 1
        const val MAX = 99
        val ONE = Finite(1)

        /** The ONLY safe way to build a Finite from unvalidated input — clamps, never throws. */
        fun of(value: Int): Finite = Finite(value.coerceIn(MIN, MAX))
    }
}
