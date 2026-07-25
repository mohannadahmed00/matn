package com.giraffe.matn.domain.model

/**
 * The single authoritative playback-session snapshot (D8; data-model.md §2.6).
 *
 * Carries exactly the fields Phase 4 ("Continue Learning") must persist — [matnId],
 * [activeVerseId], [positionMs], [speed], plus Phase 3's [settings]/[cursor]/[loopRangeVerseIds]
 * (data-model.md §2, FR-031) — so Phase 4 persists this snapshot without reshaping it
 * (Principle VI, forward-designed). Phase 2 keeps it in-memory only (FR-023).
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
    val settings: RepetitionSettings = RepetitionSettings(),
    val cursor: PlaybackCursor? = null,
    val loopRangeVerseIds: Set<String> = emptySet(),
    /** Phase 7 (practice-signal-contract.md §1): the id of the verse that just finished playing
     *  naturally (never set by user transport or an error skip), paired with a monotonically
     *  increasing [completionTick] so repeat consumers can tell successive completions of the
     *  SAME verse apart. Purely additive — read only by [com.giraffe.matn.playback.PracticeSignalRecorder]. */
    val lastCompletedVerseId: String? = null,
    val completionTick: Long = 0,
    /** T075 (US3, onboarding-permissions-contract.md §4): set once per session start when the
     *  notification-permission gate decides the in-app rationale should be shown. The caller
     *  (`PlayerBar`) reacts by presenting the sheet, then clears this via
     *  `PlaybackController.onNotificationRationaleContinue`/`onNotificationRationaleDismissed`.
     *  Purely additive — never read by anything on the audio path. */
    val showNotificationRationale: Boolean = false,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val hasSession: Boolean get() = status != PlaybackStatus.IDLE
    val mode: PlaybackMode get() = settings.mode
    val repetitionProgress: RepetitionProgress?
        get() = cursor?.let {
            RepetitionProgress(
                repetition = it.repetition,
                verseRepeatTarget = settings.verseRepeat,
                pass = it.pass,
                matnRepeatTarget = settings.matnRepeat,
            )
        }
}