package com.giraffe.matn.data.audio

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Frame offsets and cumulative timing for one MP3 [ByteSource], built in a single pass with no
 * decoding (`research.md` D8). `offsets[i+1] - offsets[i]` is frame `i`'s true length whether the
 * source is CBR or VBR, because every offset is a measured position, not a computed stride.
 */
class Mp3FrameIndex(
    val offsets: LongArray,
    val frameDurationMs: Double,
    val header: Mp3Header,
    /** Exclusive end of the last frame's data — the byte before a trailing ID3v1 block, if any. */
    val dataEnd: Long,
) {
    /** Sum of every frame's duration, rounded to the nearest millisecond. */
    val durationMs: Long get() = (offsets.size * frameDurationMs).roundToLong()

    /** The frame whose start is nearest [ms] (`split-contract.md` §3), clamped to the last frame. */
    fun frameAtMs(ms: Long): Int {
        if (offsets.isEmpty()) return 0
        val frame = (ms / frameDurationMs).roundToInt()
        return frame.coerceIn(0, offsets.size - 1)
    }

    /** The byte range spanning whole frames `[startFrame, endFrameExclusive)`. */
    fun byteRange(startFrame: Int, endFrameExclusive: Int): LongRange {
        val start = offsets[startFrame]
        val end = if (endFrameExclusive < offsets.size) offsets[endFrameExclusive] else dataEnd
        return start until end
    }

    companion object {
        private const val ID3V1_SIZE = 128

        /**
         * Skips a leading ID3v2 block, excludes a trailing ID3v1 block, and records one offset per
         * frame. Returns `null` when the source yields no valid first frame — the caller's signal
         * that this content cannot be read as MP3 (FR-003).
         */
        suspend fun build(source: ByteSource): Mp3FrameIndex? {
            var pos = id3v2SkipBytes(source)
            val effectiveEnd = source.size - if (hasId3v1(source)) ID3V1_SIZE else 0

            val offsets = mutableListOf<Long>()
            var firstHeader: Mp3Header? = null

            while (pos + 4 <= effectiveEnd) {
                val headerBytes = source.read(pos, 4)
                val header = parseHeader(headerBytes, 0) ?: break
                if (firstHeader == null) firstHeader = header
                offsets.add(pos)
                pos += header.frameLengthBytes
            }

            val header = firstHeader ?: return null
            if (offsets.isEmpty()) return null

            return Mp3FrameIndex(
                offsets = offsets.toLongArray(),
                frameDurationMs = header.samplesPerFrame * 1000.0 / header.sampleRate,
                header = header,
                dataEnd = effectiveEnd,
            )
        }

        private suspend fun id3v2SkipBytes(source: ByteSource): Long {
            if (source.size < 10) return 0L
            val head = source.read(0, 10)
            val isId3v2 = head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
            if (!isId3v2) return 0L
            val flags = head[5].toInt() and 0xFF
            val size = ((head[6].toInt() and 0x7F) shl 21) or
                ((head[7].toInt() and 0x7F) shl 14) or
                ((head[8].toInt() and 0x7F) shl 7) or
                (head[9].toInt() and 0x7F)
            val hasFooter = (flags and 0x10) != 0
            return 10L + size + if (hasFooter) 10L else 0L
        }

        private suspend fun hasId3v1(source: ByteSource): Boolean {
            if (source.size < ID3V1_SIZE) return false
            val tail = source.read(source.size - ID3V1_SIZE, 3)
            return tail[0] == 'T'.code.toByte() && tail[1] == 'A'.code.toByte() && tail[2] == 'G'.code.toByte()
        }
    }
}
