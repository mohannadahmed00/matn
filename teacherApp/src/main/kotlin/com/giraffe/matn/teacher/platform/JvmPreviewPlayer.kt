package com.giraffe.matn.teacher.platform

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.PreviewPlayer
import com.giraffe.matn.domain.audio.PreviewState
import com.giraffe.matn.domain.audio.PreviewVerse
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Opens **one** [SourceDataLine] for the whole sequence and writes successive verses' decoded PCM
 * into it — legal because every verse in a matn shares a profile (FR-005), and what makes the
 * transition between verses gapless by construction (`research.md` D9): the boundary is just the
 * next `write()`, not a new line. Never used for cutting or duration — decode-only, this phase's
 * only reason to depend on JLayer at all (`research.md` D1).
 *
 * **[play] and [playClip] suspend for the whole duration of playback.** They deliberately do not
 * launch into a private scope and return immediately: a caller that cannot await the end has no
 * way to know when to clear its own "now playing" state, and the version of this class that did
 * launch-and-return produced exactly that bug — the UI's play/stop control desynced from the audio
 * and a second press started a second overlapping playback instead of stopping the first.
 */
class JvmPreviewPlayer(
    private val cache: PreviewCache,
) : PreviewPlayer {

    private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
    override val state: StateFlow<PreviewState> = _state.asStateFlow()

    /**
     * One playback attempt, owning its own cancellation flag and audio line.
     *
     * Per-attempt rather than shared `@Volatile` flags on the player: a single shared `stopped`
     * flag has to be reset to `false` when the next playback starts, and the previous job — blocked
     * inside `SourceDataLine.write()` — routinely failed to observe the brief `true` window before
     * it flipped back, so it never exited and both attempts sounded at once. A token the old job
     * owns can never be un-cancelled by a newer one.
     */
    private class Playback {
        @Volatile var cancelled = false
        @Volatile var paused = false
        @Volatile var line: SourceDataLine? = null
    }

    @Volatile private var current: Playback? = null

    override suspend fun play(verses: List<PreviewVerse>, startIndex: Int) {
        val playback = begin()
        try {
            withContext(Dispatchers.IO) {
                for (i in startIndex until verses.size) {
                    if (playback.cancelled) break
                    val verse = verses[i]
                    val fileRef = verse.fileRef
                    if (fileRef == null) {
                        _state.value = PreviewState.MissingAudio(verse.displayNumber)
                        continue
                    }
                    _state.value = PreviewState.Buffering(verse.displayNumber)
                    val file = when (val cached = cache.getOrDownload(fileRef)) {
                        is Resource.Success -> cached.data
                        is Resource.Failure -> {
                            _state.value = PreviewState.MissingAudio(verse.displayNumber)
                            continue
                        }
                    }
                    // The line is kept on `playback` and reused across verses — that reuse is the
                    // gaplessness (research D9).
                    decodeInto(playback, file.readBytes(), verse.displayNumber)
                }
            }
        } finally {
            finish(playback)
        }
    }

    /** Same decode-and-write path as [play], on one already-in-memory clip — no cache lookup and
     * no download, because the bytes are a slice the split screen has just produced locally. */
    override suspend fun playClip(bytes: ByteArray, displayNumber: Int) {
        val playback = begin()
        try {
            withContext(Dispatchers.IO) { decodeInto(playback, bytes, displayNumber) }
        } finally {
            finish(playback)
        }
    }

    /** Stops whatever is playing and installs a fresh token, so at most one attempt is ever live. */
    private fun begin(): Playback {
        stop()
        val playback = Playback()
        current = playback
        return playback
    }

    /**
     * `drain()` **blocks until every buffered frame has played** — right when a clip ends naturally,
     * and exactly wrong after an explicit stop, where the teacher would keep hearing audio after
     * pressing it. A cancelled attempt discards its buffer instead.
     *
     * The terminal `Idle` is published only by the attempt that is still current, so a job being
     * torn down by its successor cannot stamp `Idle` over the clip that just replaced it.
     */
    private fun finish(playback: Playback) {
        playback.line?.let { line ->
            runCatching { if (playback.cancelled) line.flush() else line.drain() }
            runCatching { line.close() }
        }
        playback.line = null
        if (current === playback) {
            current = null
            _state.value = PreviewState.Idle
        }
    }

    private suspend fun decodeInto(playback: Playback, bytes: ByteArray, displayNumber: Int) {
        val bitstream = Bitstream(ByteArrayInputStream(bytes))
        val decoder = Decoder()
        var samplesWritten = 0L
        try {
            while (!playback.cancelled) {
                while (playback.paused && !playback.cancelled) delay(PAUSE_POLL_MS)
                if (playback.cancelled) break
                val header = bitstream.readFrame() ?: break
                val output = decoder.decodeFrame(header, bitstream) as? SampleBuffer
                if (output != null) {
                    if (playback.line == null) {
                        val format = AudioFormat(output.sampleFrequency.toFloat(), 16, output.channelCount, true, false)
                        playback.line = AudioSystem.getSourceDataLine(format).apply { open(format); start() }
                    }
                    val pcm = interleavedShortsToLittleEndianBytes(output.buffer, output.bufferLength)
                    playback.line?.write(pcm, 0, pcm.size)
                    samplesWritten += output.bufferLength / output.channelCount.coerceAtLeast(1)
                    val positionMs = samplesWritten * 1000 / output.sampleFrequency.coerceAtLeast(1)
                    if (!playback.cancelled) _state.value = PreviewState.Playing(displayNumber, positionMs)
                }
                bitstream.closeFrame()
            }
        } finally {
            runCatching { bitstream.close() }
        }
    }

    override fun pause() {
        val playback = current ?: return
        playback.paused = true
        (state.value as? PreviewState.Playing)?.let { _state.value = PreviewState.Paused(it.verseNumber) }
    }

    override fun resume() {
        current?.paused = false
    }

    override fun stop() {
        val playback = current
        current = null
        if (playback != null) {
            playback.cancelled = true
            playback.paused = false
            // Flush from here rather than waiting for the writer to notice the flag: it silences
            // the line immediately and unblocks an in-flight write(), so the loop reaches its next
            // cancellation check promptly. The attempt's own `finish` still closes the line.
            runCatching { playback.line?.flush() }
        }
        _state.value = PreviewState.Idle
    }

    private companion object {
        const val PAUSE_POLL_MS = 50L
    }
}

private fun interleavedShortsToLittleEndianBytes(samples: ShortArray, length: Int): ByteArray {
    val out = ByteArray(length * 2)
    for (i in 0 until length) {
        val s = samples[i].toInt()
        out[i * 2] = (s and 0xFF).toByte()
        out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return out
}
