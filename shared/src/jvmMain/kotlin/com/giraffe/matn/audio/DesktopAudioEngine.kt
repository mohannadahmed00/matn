package com.giraffe.matn.audio

import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.EnginePlaybackInfo
import com.giraffe.matn.domain.model.AudioTrack
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop [AudioEngine] built on the JDK's built-in `javax.sound.sampled` player. Holds no business
 * logic (Principle IV) — it decodes/plays whatever `AudioSystem` can open for the current track's
 * URI and translates native line events to [AudioEngineEvent]s; all playback *decisions* live in
 * `PlaybackController`, exactly as for `Media3AudioEngine`/`AvQueueAudioEngine`.
 *
 * `javax.sound.sampled` decodes WAV/AU/AIFF out of the box but ships no MP3 codec — a track whose
 * format the JDK cannot open emits [AudioEngineEvent.TrackError] (the same skip-on-error path
 * `Media3AudioEngine` uses for a native decode failure) instead of crashing. [setSpeed] is accepted
 * for interface parity but not applied: real-time resampling is out of scope for this minimal
 * engine, so the reported rate never changes.
 *
 * A single daemon player thread owns the `SourceDataLine` and drains a blocking command queue, so
 * calls from any caller thread (play/pause/seek/stop/…) are applied in order without racing the
 * decode/write loop.
 */
class DesktopAudioEngine : AudioEngine {

    private sealed interface Command {
        data class SetQueue(val tracks: List<AudioTrack>, val startIndex: Int) : Command
        data object Play : Command
        data object Pause : Command
        data object Stop : Command
        data class SeekTo(val positionMs: Long) : Command
        data class SeekToTrack(val index: Int) : Command
        data class ReplaceUpcoming(val tracks: List<AudioTrack>) : Command
        data object DropConsumed : Command
        data object Release : Command
    }

    private val commands = LinkedBlockingQueue<Command>()

    private val _playbackInfo = MutableStateFlow(
        EnginePlaybackInfo(isPlaying = false, currentIndex = -1, positionMs = 0, durationMs = 0),
    )
    override val playbackInfo: StateFlow<EnginePlaybackInfo> = _playbackInfo.asStateFlow()

    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = Int.MAX_VALUE)
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    // Everything below is touched only on the player thread.
    private var tracks: List<AudioTrack> = emptyList()
    private var index: Int = -1
    private var wantsPlaying: Boolean = false
    private var pendingSeekMs: Long = 0L

    private var currentInput: AudioInputStream? = null
    private var currentLine: SourceDataLine? = null
    private var currentBaseMs: Long = 0L
    private var bytesWritten: Long = 0L

    init {
        Thread(::runLoop, "DesktopAudioEngine").apply {
            isDaemon = true
            start()
        }
    }

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int) {
        commands.put(Command.SetQueue(tracks, startIndex))
    }

    override fun play() = commands.put(Command.Play)
    override fun pause() = commands.put(Command.Pause)
    override fun stop() = commands.put(Command.Stop)
    override fun seekTo(positionMs: Long) = commands.put(Command.SeekTo(positionMs))
    override fun seekToTrack(index: Int) = commands.put(Command.SeekToTrack(index))
    override fun replaceUpcoming(tracks: List<AudioTrack>) = commands.put(Command.ReplaceUpcoming(tracks))
    override fun dropConsumed() = commands.put(Command.DropConsumed)
    override fun release() = commands.put(Command.Release)

    override fun setSpeed(multiplier: Float) {
        // Accepted for interface parity; not applied — see class doc.
    }

    private fun runLoop() {
        while (true) {
            when (val cmd = commands.take()) {
                Command.Release -> {
                    closeCurrent()
                    return
                }
                else -> handle(cmd)
            }
            while (wantsPlaying && commands.isEmpty()) {
                if (!pumpOnce()) break
            }
        }
    }

    private fun handle(cmd: Command) {
        when (cmd) {
            is Command.SetQueue -> {
                closeCurrent()
                tracks = cmd.tracks
                index = if (cmd.tracks.isEmpty()) -1 else cmd.startIndex.coerceIn(0, cmd.tracks.lastIndex)
                wantsPlaying = false
                pendingSeekMs = 0L
                pushInfo(positionMs = 0)
                if (cmd.tracks.isNotEmpty()) _events.tryEmit(AudioEngineEvent.Ready)
            }
            Command.Play -> {
                if (tracks.isEmpty()) return
                wantsPlaying = true
                if (currentLine == null) openCurrent(pendingSeekMs) else currentLine?.start()
                pushInfo()
            }
            Command.Pause -> {
                wantsPlaying = false
                currentLine?.stop()
                pushInfo()
            }
            Command.Stop -> {
                wantsPlaying = false
                closeCurrent()
                tracks = emptyList()
                index = -1
                pendingSeekMs = 0L
                pushInfo(positionMs = 0)
            }
            is Command.SeekTo -> {
                if (tracks.isEmpty()) return
                pendingSeekMs = cmd.positionMs.coerceAtLeast(0)
                closeCurrent()
                if (wantsPlaying) openCurrent(pendingSeekMs) else pushInfo(positionMs = pendingSeekMs)
            }
            is Command.SeekToTrack -> {
                if (tracks.isEmpty()) return
                closeCurrent()
                index = cmd.index.coerceIn(0, tracks.lastIndex)
                pendingSeekMs = 0L
                if (wantsPlaying) openCurrent(0L) else pushInfo(positionMs = 0)
            }
            is Command.ReplaceUpcoming -> {
                if (tracks.isNotEmpty()) tracks = tracks.take(index + 1) + cmd.tracks
            }
            Command.DropConsumed -> {
                if (index > 0) {
                    tracks = tracks.drop(index)
                    index = 0
                }
            }
            Command.Release -> Unit // handled in runLoop
        }
    }

    /** Opens the current track's stream/line at [seekMs] and starts the line. On failure emits [AudioEngineEvent.TrackError]. */
    private fun openCurrent(seekMs: Long) {
        val track = tracks.getOrNull(index) ?: return
        try {
            val url = URI(track.uri).toURL()
            val rawStream = AudioSystem.getAudioInputStream(url)
            val base = rawStream.format
            val pcmStream = if (base.encoding == AudioFormat.Encoding.PCM_SIGNED) {
                rawStream
            } else {
                val pcmFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.sampleRate,
                    16,
                    base.channels,
                    base.channels * 2,
                    base.sampleRate,
                    false,
                )
                AudioSystem.getAudioInputStream(pcmFormat, rawStream)
            }
            skipTo(pcmStream, seekMs)
            val info = DataLine.Info(SourceDataLine::class.java, pcmStream.format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(pcmStream.format)
            line.start()
            currentInput = pcmStream
            currentLine = line
            currentBaseMs = seekMs
            bytesWritten = 0L
            pushInfo(positionMs = seekMs)
        } catch (t: Throwable) {
            // Unsupported format (e.g. MP3 — the JDK ships no built-in decoder) or line unavailable.
            closeCurrent()
            wantsPlaying = false
            _events.tryEmit(AudioEngineEvent.TrackError(index))
        }
    }

    private fun skipTo(stream: AudioInputStream, seekMs: Long) {
        if (seekMs <= 0L) return
        val frameSize = stream.format.frameSize.coerceAtLeast(1)
        val bytesPerMs = stream.format.frameRate * frameSize / 1000.0
        var remaining = (seekMs * bytesPerMs).toLong()
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0L) break
            remaining -= skipped
        }
    }

    /** Writes one buffer's worth of the current track to the line. Returns false when the loop should stop. */
    private fun pumpOnce(): Boolean {
        val input = currentInput ?: return false
        val line = currentLine ?: return false
        val buffer = ByteArray(4096)
        val read = try {
            input.read(buffer)
        } catch (t: Throwable) {
            closeCurrent()
            wantsPlaying = false
            _events.tryEmit(AudioEngineEvent.TrackError(index))
            return false
        }
        if (read < 0) return advanceTrack()
        line.write(buffer, 0, read)
        bytesWritten += read
        pushInfo()
        return true
    }

    /** Advances to the next track (gapless-ish), or ends the queue when this was the last one. */
    private fun advanceTrack(): Boolean {
        currentLine?.drain()
        closeCurrent()
        val next = index + 1
        if (next > tracks.lastIndex) {
            wantsPlaying = false
            pushInfo(positionMs = 0)
            _events.tryEmit(AudioEngineEvent.QueueEnded)
            return false
        }
        index = next
        pendingSeekMs = 0L
        _events.tryEmit(AudioEngineEvent.TrackTransition(index))
        openCurrent(0L)
        return currentLine != null
    }

    private fun closeCurrent() {
        try {
            currentLine?.stop()
            currentLine?.close()
        } catch (_: Throwable) {
        }
        try {
            currentInput?.close()
        } catch (_: Throwable) {
        }
        currentLine = null
        currentInput = null
        bytesWritten = 0L
    }

    private fun currentPositionMs(): Long {
        val stream = currentInput ?: return pendingSeekMs
        val frameSize = stream.format.frameSize.coerceAtLeast(1)
        val frameRate = stream.format.frameRate.takeIf { it > 0f } ?: return currentBaseMs
        return currentBaseMs + ((bytesWritten / frameSize) / frameRate * 1000).toLong()
    }

    private fun pushInfo(positionMs: Long = currentPositionMs()) {
        _playbackInfo.value = EnginePlaybackInfo(
            isPlaying = wantsPlaying,
            currentIndex = index,
            positionMs = positionMs,
            durationMs = tracks.getOrNull(index)?.durationMs ?: 0L,
        )
    }
}
