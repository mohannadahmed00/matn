package com.giraffe.matn.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.EnginePlaybackInfo
import com.giraffe.matn.domain.model.AudioTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android [AudioEngine] built on Media3 `ExoPlayer` (D2/D3 / contracts/audio-engine.md).
 *
 * Holds **no business logic** (Principle IV): it translates primitive calls to ExoPlayer and
 * native callbacks to [AudioEngineEvent]s. Gapless verse-to-verse is automatic via a single
 * `setMediaItems` playlist — never stop/recreate the player per verse (FR-006/FR-022).
 *
 * Audio focus (`setAudioAttributes(..., handleAudioFocus = true)`) maps focus loss /
 * `ACTION_AUDIO_BECOMING_NOISY` to [AudioEngineEvent.InterruptionBegan]/[AudioEngineEvent.InterruptionEnded]
 * — the controller applies the resume policy (D5). Pitch-preserved speed via
 * `setPlaybackParameters(speed, 1.0f)` (D6). Background foreground-service + MediaSession /
 * notification + lock-screen controls are added in US3 (`MatnMediaSessionService`) wired to the
 * same `Player`/controller.
 */
class Media3AudioEngine(context: Context) : AudioEngine {

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .build()

    private val _playbackInfo = MutableStateFlow(
        EnginePlaybackInfo(
            isPlaying = false,
            currentIndex = -1,
            positionMs = 0,
            durationMs = 0,
        ),
    )
    override val playbackInfo: StateFlow<EnginePlaybackInfo> = _playbackInfo.asStateFlow()

    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = Int.MAX_VALUE)
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    /** True until the first `STATE_READY` after a new queue is prepared; used to emit `Ready`. */
    private var awaitingFirstReady: Boolean = false

    /**
     * Main-thread ticker that pushes position progress into [playbackInfo] while playing. ExoPlayer
     * only fires listener callbacks on state changes / transitions — it emits nothing during steady
     * playback — so the snapshot the controller polls must be refreshed on a timer.
     */
    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionTicker = object : Runnable {
        override fun run() {
            if (player.isPlaying) pushInfo()
            positionHandler.postDelayed(this, POSITION_TICKER_INTERVAL_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            pushInfo()
            _events.tryEmit(AudioEngineEvent.TrackTransition(player.currentMediaItemIndex))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    if (awaitingFirstReady) {
                        awaitingFirstReady = false
                        _events.tryEmit(AudioEngineEvent.Ready)
                    }
                    pushInfo()
                }
                Player.STATE_ENDED -> {
                    _events.tryEmit(AudioEngineEvent.QueueEnded)
                    pushInfo()
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _events.tryEmit(AudioEngineEvent.TrackError(player.currentMediaItemIndex))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // No interruption synthesis here — see onPlayWhenReadyChanged for the audio-focus/
            // becoming-noisy distinction (D5). This callback only refreshes the snapshot.
            pushInfo()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Media3 distinguishes *why* playWhenReady flipped. Map only OS causes to interruption
            // events; USER_REQUEST / REMOTE / END_OF_MEDIA_ITEM are not interruptions.
            //
            // NOTE: Media3 surfaces a single AUDIO_FOCUS_LOSS reason here (it does not split
            // transient vs. non-transient), so we treat any focus loss as a transient
            // interruption and let the controller's resume policy (D5) apply. Headphone unplug
            // is the non-transient case and arrives via AUDIO_BECOMING_NOISY. Final behavior for
            // rows 10–11 of quickstart.md §B is validated on-device.
            when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ->
                    if (!playWhenReady) _events.tryEmit(AudioEngineEvent.InterruptionBegan(transient = true))
                    else _events.tryEmit(AudioEngineEvent.InterruptionEnded(shouldResume = true))
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                    if (!playWhenReady) _events.tryEmit(AudioEngineEvent.InterruptionBegan(transient = false))
                else -> { /* USER_REQUEST / REMOTE / END_OF_MEDIA_ITEM: not an interruption */ }
            }
            pushInfo()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            pushInfo()
        }
    }

    init {
        player.addListener(listener)
        positionHandler.post(positionTicker)
    }

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int) {
        if (tracks.isEmpty()) {
            player.clearMediaItems()
            _playbackInfo.value = _playbackInfo.value.copy(currentIndex = -1, durationMs = 0)
            return
        }
        val mediaItems = tracks.map { MediaItem.fromUri(it.uri) }
        player.setMediaItems(mediaItems, startIndex.coerceIn(0, tracks.lastIndex), 0L)
        awaitingFirstReady = true
        player.prepare()
    }

    override fun play() {
        // Recover from STATE_IDLE: after `onPlayerError` the player sits in IDLE and ignores
        // `play()` until `prepare()` is called again (FR-020 skip-on-error path).
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }
    override fun pause() { player.pause() }
    override fun stop() {
        player.stop()
        player.clearMediaItems()
        awaitingFirstReady = false
        _playbackInfo.value = _playbackInfo.value.copy(currentIndex = -1, positionMs = 0, durationMs = 0, isPlaying = false)
    }

    override fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0)) }

    override fun seekToTrack(index: Int) {
        if (player.mediaItemCount == 0) return
        player.seekTo(index.coerceIn(0, player.mediaItemCount - 1), 0L)
        // Recover from STATE_IDLE after an `onPlayerError`: a bare seek is ignored until prepare()
        // is called again, so playback stalls instead of skipping. Re-prepares here (FR-020).
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    override fun setSpeed(multiplier: Float) {
        player.playbackParameters = PlaybackParameters(multiplier, 1.0f)
    }

    override fun replaceUpcoming(tracks: List<AudioTrack>) {
        if (player.mediaItemCount == 0) return
        val from = player.currentMediaItemIndex + 1
        player.replaceMediaItems(from, player.mediaItemCount, tracks.map { MediaItem.fromUri(it.uri) })
    }

    override fun dropConsumed() {
        val current = player.currentMediaItemIndex
        if (current > 0) player.removeMediaItems(0, current)
    }

    override fun release() {
        positionHandler.removeCallbacks(positionTicker)
        player.removeListener(listener)
        player.release()
    }

    private fun pushInfo() {
        _playbackInfo.value = EnginePlaybackInfo(
            isPlaying = player.isPlaying,
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0),
        )
    }

    companion object {
        /** Refresh cadence for the position snapshot pushed into [playbackInfo] (~5 Hz). */
        private const val POSITION_TICKER_INTERVAL_MS = 200L
    }
}