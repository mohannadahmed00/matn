package com.giraffe.matn.domain.model

/**
 * The result of removing a matn's content (data-model §2.4). The single place where Android and
 * iOS genuinely differ: Android reclaims disk space immediately, iOS exposes no API to force-purge
 * On-Demand Resources (research D2). Both variants carry [bytes] so the post-removal message can
 * state what was reclaimed (Android) or what was released pending reclaim (iOS).
 */
sealed interface RemovalOutcome {

    /** Disk space reclaimed immediately (Android). */
    data class Reclaimed(val bytes: Long) : RemovalOutcome

    /**
     * Tag released; the OS reclaims when it needs space (iOS). The confirmation and post-removal
     * copy MUST NOT promise an immediate figure for this variant (research D2 / SC-004a).
     */
    data class ReleasedPendingSystemReclaim(val bytes: Long) : RemovalOutcome
}