package com.giraffe.matn.domain.model

/**
 * The single authoritative playback-session snapshot (D8; data-model.md §2.6).
 *
 * Carries exactly the fields Phase 4 ("Continue Learning") must persist — [matnId],
 * [activeVerseId], [positionMs], [speed] — so Phase 4 persists this snapshot without reshaping
 * it (Principle VI, forward-designed). Phase 2 keeps it in-memory only (FR-023).
 */
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val matnId: String? = null,
    val activeVerseId: String? = null,
    val activeIndex: Int = -1,
    val activeDisplayNumber: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: PlaybackSpeed = PlaybackSpeed.DEFAULT,
    val pauseReason: PauseReason? = null,
    val notice: PlaybackNotice? = null,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val hasSession: Boolean get() = status != PlaybackStatus.IDLE
}