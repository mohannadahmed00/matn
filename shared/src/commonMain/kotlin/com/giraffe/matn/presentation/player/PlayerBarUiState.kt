package com.giraffe.matn.presentation.player

import com.giraffe.matn.domain.model.PlaybackMode
import com.giraffe.matn.domain.model.PlaybackSpeed
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings

/**
 * UI-state for the persistent player bar (data-model.md §5.2/§6.1). A pure projection of
 * `PlaybackController.state`: `visible = hasSession`; carries everything the bar needs to render
 * transport (play/pause/stop, next/previous, scrub, speed) and the now-playing label, plus the
 * Phase 3 drill projection (mode chip, repetition/pass indicators, current settings).
 */
data class PlayerBarUiState(
    val visible: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val matnId: String? = null,
    val activeVerseId: String? = null,
    val activeVerseDisplayNumber: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: PlaybackSpeed = PlaybackSpeed.DEFAULT,
    val canNext: Boolean = false,
    val canPrevious: Boolean = false,
    val mode: PlaybackMode = PlaybackMode.NORMAL,
    val repetition: Int = 1,
    val verseRepeatTarget: RepeatCount = RepeatCount.ONE,
    val pass: Int = 1,
    val matnRepeatTarget: RepeatCount = RepeatCount.ONE,
    val settings: RepetitionSettings = RepetitionSettings(),
    /** T076 (US3): mirrors [com.giraffe.matn.domain.model.PlaybackState.showNotificationRationale]. */
    val showNotificationRationale: Boolean = false,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val isLoading: Boolean get() = status == PlaybackStatus.LOADING
    val canPlayPause: Boolean get() = status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED
}