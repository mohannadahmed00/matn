package com.giraffe.matn.domain.model

/**
 * The result of removing a matn's content (delivery-contract §2).
 *
 * Phase 13 collapsed this to a single value. `ReleasedPendingSystemReclaim` existed only to describe
 * iOS On-Demand Resources' deferred purge — the app could release a tag but not force the OS to
 * reclaim, so the post-removal copy could not promise a figure. With content now living in ordinary
 * app-private files, deleting is immediate on every platform, which is also what makes SC-008's ±5%
 * and SC-009's "0 bytes" directly observable rather than eventually-true.
 */
sealed interface RemovalOutcome {

    /** Disk space reclaimed immediately. [bytes] is what the removal actually freed. */
    data class Reclaimed(val bytes: Long) : RemovalOutcome
}
