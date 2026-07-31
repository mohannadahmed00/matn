package com.giraffe.matn.teacher.platform

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.data.audio.Mp3FrameIndex
import com.giraffe.matn.domain.audio.AudioProbe
import com.giraffe.matn.domain.audio.AudioProfile
import com.giraffe.matn.domain.audio.ProbeResult
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.min

/** The only place `:teacherApp` decodes MP3 to PCM (`research.md` D1) — [probe] never does, it
 * reads only the frame index; [peaks] streams frame-by-frame and discards PCM immediately (D7). */
class JLayerAudioProbe : AudioProbe {

    override suspend fun probe(source: ByteSource): Resource<ProbeResult> {
        val index = Mp3FrameIndex.build(source) ?: return Resource.Failure(AppError.Storage("not a readable MP3 file"))
        return Resource.Success(
            ProbeResult(
                durationMs = index.durationMs,
                profile = AudioProfile(sampleRate = index.header.sampleRate, channels = index.header.channels),
                frameCount = index.offsets.size,
            ),
        )
    }

    override suspend fun peaks(source: ByteSource, buckets: Int): Resource<FloatArray> {
        val index = Mp3FrameIndex.build(source) ?: return Resource.Failure(AppError.Storage("not a readable MP3 file"))
        val totalFrames = index.offsets.size
        val peaks = FloatArray(buckets)

        val bitstream = Bitstream(ByteSourceInputStream(source))
        val decoder = Decoder()
        var frameIndex = 0
        try {
            while (true) {
                val header = bitstream.readFrame() ?: break
                val output = decoder.decodeFrame(header, bitstream) as? SampleBuffer
                if (output != null) {
                    val bucket = (frameIndex * buckets / totalFrames.coerceAtLeast(1)).coerceIn(0, buckets - 1)
                    var peak = peaks[bucket]
                    val samples = output.buffer
                    val length = output.bufferLength
                    for (i in 0 until length) {
                        val normalized = abs(samples[i].toFloat()) / 32768f
                        if (normalized > peak) peak = normalized
                    }
                    peaks[bucket] = min(peak, 1f)
                }
                bitstream.closeFrame()
                frameIndex++
            }
        } finally {
            bitstream.close()
        }

        return Resource.Success(peaks)
    }
}

/** Adapts a random-access [ByteSource] to the sequential [InputStream] JLayer's [Bitstream]
 * requires, so [peaks] can decode any [ByteSource] — an in-memory one in tests, a file-backed one
 * in the app — without JLayer ever seeing the file system directly. */
private class ByteSourceInputStream(private val source: ByteSource) : InputStream() {
    private var position = 0L

    override fun read(): Int {
        if (position >= source.size) return -1
        val byte = runBlocking { source.read(position, 1) }
        position++
        return byte[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= source.size) return -1
        val toRead = min(len.toLong(), source.size - position).toInt()
        val bytes = runBlocking { source.read(position, toRead) }
        bytes.copyInto(b, off)
        position += toRead
        return toRead
    }
}
