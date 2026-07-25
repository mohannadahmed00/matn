package com.giraffe.matn.domain.model

/**
 * Session lifecycle states (data-model.md §2.4). `ENDED` is "finished the last verse and
 * stopped" (FR-007); `STOPPED` collapses to `IDLE` (session cleared, highlight cleared, FR-004).
 */
enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, ENDED }

/**
 * Why playback is paused (D5 / FR-019). Drives resume policy: only
 * [TRANSIENT_INTERRUPTION] auto-resumes when focus returns; [USER] and
 * [NON_TRANSIENT_INTERRUPTION] require a manual resume.
 */
enum class PauseReason { USER, TRANSIENT_INTERRUPTION, NON_TRANSIENT_INTERRUPTION }

/**
 * One-shot user-facing notices emitted by the session state machine (data-model.md §2.7).
 * Consumed-once via `PlaybackController.consumeNotice()`.
 */
sealed interface PlaybackNotice {
    /** A verse's audio failed to load/play and was skipped (FR-020). */
    data class SkippedMissingVerse(val verseId: String) : PlaybackNotice
    /** The last verse finished and playback stopped (FR-007). */
    data object ReachedEnd : PlaybackNotice
    /** No track in the queue is playable (FR-020). */
    data object NoPlayableAudio : PlaybackNotice
    /** Phase 8 (FR-011): the matn's content is not installed; the UI shows install prompt. */
    data class ContentNotInstalled(val matnId: String) : PlaybackNotice
}