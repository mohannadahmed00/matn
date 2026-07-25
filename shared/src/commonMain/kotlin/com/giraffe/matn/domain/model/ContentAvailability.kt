package com.giraffe.matn.domain.model

/**
 * The atomic per-matn availability of a matn's content (FR-001, spec Clarifications 2026-07-25).
 * Exactly three states, no partial state, no per-verse presence tracking. Computed per read from
 * the delivery platform plus the `content_pack` row (research D5) — **never persisted**.
 */
sealed interface ContentAvailability {

    /**
     * Not installed. [failure] is non-null when the last install attempt failed; every value of
     * [DeliveryFailure] is retryable from the UI. `Evicted` renders as plain "not installed", not
     * as an error state the student caused.
     */
    data class NotInstalled(val failure: DeliveryFailure? = null) : ContentAvailability

    /** An install is in flight. Holds the live [DeliveryProgress]. */
    data class Installing(val progress: DeliveryProgress) : ContentAvailability

    /**
     * Installed and present right now. [occupiedBytes] follows data-model §2.1's three-step
     * priority: the starter matn always reports its measured declared size, an installed on-demand
     * matn reports the size of its pack directory, and a matn the platform reports as installed
     * with no resolvable path is treated as `NotInstalled(Evicted)` instead of `Installed(0)`.
     */
    data class Installed(val occupiedBytes: Long) : ContentAvailability
}