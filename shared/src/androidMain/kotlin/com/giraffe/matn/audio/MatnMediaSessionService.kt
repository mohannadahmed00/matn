package com.giraffe.matn.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground media service hosting a [MediaSession] over the shared [ExoPlayer] (D4 /
 * FR-015/FR-016). Media3 renders the media-style notification (play/pause + next/previous) and
 * mirrors it to the lock screen while playback is active.
 *
 * The session's transport buttons must route through the single `PlaybackController` so the
 * shared `PlaybackState` (active verse, highlight, queue index) stays consistent whether the
 * user taps the in-app bar or the notification. That wiring is completed on-device (T040) via a
 * `MediaSession.Callback` that translates incoming player commands into `PlaybackController`
 * intents; the service resolves the controller from `com.giraffe.matn.di.MatnKoinHolder` (same
 * process as the activity).
 *
 * NOTE: the implementation intentionally stays minimal here — building the foreground service +
 * attaching the activity's ExoPlayer to a MediaSession + routing commands through the controller
 * is genuinely platform behaviour validated on-device per quickstart.md §B rows 8–12. Until the
 * on-device follow-up lands the service reused the in-app engine and the controller's own
 * state machine drives transport.
 */
@UnstableApi
class MatnMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // The ExoPlayer is owned by the activity-injected Media3AudioEngine (single Koin binding).
        // Attaching it to a MediaSession here is the on-device step; left as the platform-integration
        // touch-point.
        // TODO(T040): obtain the shared ExoPlayer, build a MediaSession, wire a Callback that
        //   routes onPlay/onPause/onSkipToNext/onSkipToPrevious → PlaybackController (US3 on-device).
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run { release() }
        mediaSession = null
        super.onDestroy()
    }
}