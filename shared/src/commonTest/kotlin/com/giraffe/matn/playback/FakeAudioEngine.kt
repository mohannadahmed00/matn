package com.giraffe.matn.playback

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
 * Pure in-memory [AudioEngine] for `commonTest` (Principle V). Records primitive calls and lets
 * tests **emit scripted [AudioEngineEvent]s** to drive `PlaybackController` through every
 * transition in data-model.md §6 with no device, audio, or network.
 */
class FakeAudioEngine(initialInfo: EnginePlaybackInfo = EnginePlaybackInfo(
    isPlaying = false,
    currentIndex = -1,
    positionMs = 0,
    durationMs = 0,
)) : AudioEngine {

    var lastQueue: List<AudioTrack>? = null
        private set
    var startIndex: Int = 0
        private set
    var playCalled: Boolean = false
        private set
    var pauseCalled: Boolean = false
        private set
    var stopCalled: Boolean = false
        private set
    var seekedToMs: Long? = null
        private set
    var seekedToTrack: Int? = null
        private set
    var speed: Float? = null
        private set
    var released: Boolean = false
        private set

    /** Simulated playlist so window behavior (replaceUpcoming/dropConsumed) is assertable. */
    var playlist: List<AudioTrack> = emptyList()
    var currentIndex: Int = 0
    val upcomingReplacements = mutableListOf<List<AudioTrack>>()
    var dropConsumedCount: Int = 0
        private set

    private val _playbackInfo = MutableStateFlow(initialInfo)
    override val playbackInfo: StateFlow<EnginePlaybackInfo> = _playbackInfo.asStateFlow()

    private val _events = MutableSharedFlow<AudioEngineEvent>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int) {
        lastQueue = tracks
        this.startIndex = startIndex
        playlist = tracks
        currentIndex = startIndex
        _playbackInfo.value = _playbackInfo.value.copy(currentIndex = startIndex)
    }

    override fun play() { playCalled = true }
    override fun pause() { pauseCalled = true }
    override fun stop() { stopCalled = true }
    override fun seekTo(positionMs: Long) { seekedToMs = positionMs }
    override fun seekToTrack(index: Int) { seekedToTrack = index }
    override fun setSpeed(multiplier: Float) { speed = multiplier }
    override fun release() { released = true }

    override fun replaceUpcoming(tracks: List<AudioTrack>) {
        upcomingReplacements.add(tracks)
        playlist = playlist.take(currentIndex + 1) + tracks
    }

    override fun dropConsumed() {
        if (currentIndex > 0) {
            playlist = playlist.drop(currentIndex)
            currentIndex = 0
        }
        dropConsumedCount++
    }

    /** Push a scripted event into the controller's collector. */
    suspend fun emit(event: AudioEngineEvent) { _events.emit(event) }

    /** Replace the polled snapshot. */
    fun setInfo(
        isPlaying: Boolean = _playbackInfo.value.isPlaying,
        currentIndex: Int = _playbackInfo.value.currentIndex,
        positionMs: Long = _playbackInfo.value.positionMs,
        durationMs: Long = _playbackInfo.value.durationMs,
    ) {
        _playbackInfo.value = EnginePlaybackInfo(isPlaying, currentIndex, positionMs, durationMs)
    }

    fun resetCalls() {
        playCalled = false
        pauseCalled = false
        stopCalled = false
        seekedToMs = null
        seekedToTrack = null
        speed = null
    }
}