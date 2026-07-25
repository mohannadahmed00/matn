package com.giraffe.matn.presentation.player

import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.base.BaseViewModel

/**
 * Thin reader of [PlaybackController.state] and forwarder of transport intents (Principle II).
 * Owns no state of its own; it maps `PlaybackState` → [PlayerBarUiState] (data-model.md §5.2)
 * and emits intents (`onPlayPause`, `onStop`, `onNext`, `onPrevious`, `onSeek`, `onSpeedSelected`).
 */
class PlayerBarViewModel(
    private val controller: PlaybackController,
) : BaseViewModel<PlayerBarUiState>(PlayerBarUiState()) {

    init {
        controller.state.collectInto { state -> setState { state.toUiState() } }
    }

    fun onPlayPause() {
        when (controller.state.value.status) {
            PlaybackStatus.PLAYING -> controller.pause()
            PlaybackStatus.PAUSED -> controller.resume()
            else -> {}
        }
    }

    fun onStop() = controller.stop()
    fun onNext() = controller.next()
    fun onPrevious() = controller.previous()
    fun onSeek(positionMs: Long) = controller.seekTo(positionMs)
    fun onSpeedSelected() = controller.setSpeed(controller.state.value.speed.next())

    fun consumeNotice() = controller.consumeNotice()

    /** T076 (US3): the rationale sheet's two terminal actions — forwarded, no logic here. */
    fun onNotificationRationaleContinue() = controller.onNotificationRationaleContinue()
    fun onNotificationRationaleDismissed() = controller.onNotificationRationaleDismissed()

    /** Forwarders for the repetition drill (US1/US3); no logic beyond forwarding (Principle II). */
    fun onVerseRepeatSelected(count: RepeatCount) = controller.setVerseRepeat(count)
    fun onMatnRepeatSelected(count: RepeatCount) = controller.setMatnRepeat(count)

    private fun PlaybackState.toUiState(): PlayerBarUiState {
        return PlayerBarUiState(
            visible = hasSession,
            status = status,
            matnId = matnId,
            activeVerseId = activeVerseId,
            activeVerseDisplayNumber = activeDisplayNumber,
            positionMs = positionMs,
            durationMs = durationMs,
            speed = speed,
            // Conservative: always allow next/previous; controller enforces boundaries (FR-012).
            canNext = hasSession && status != PlaybackStatus.ENDED,
            canPrevious = hasSession,
            mode = mode,
            repetition = repetitionProgress?.repetition ?: 1,
            verseRepeatTarget = settings.verseRepeat,
            pass = repetitionProgress?.pass ?: 1,
            matnRepeatTarget = settings.matnRepeat,
            settings = settings,
            showNotificationRationale = showNotificationRationale,
        )
    }
}