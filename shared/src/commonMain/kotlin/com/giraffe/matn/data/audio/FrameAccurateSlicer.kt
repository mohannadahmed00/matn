package com.giraffe.matn.data.audio

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.AudioSlicer
import com.giraffe.matn.domain.audio.VerseRange
import com.giraffe.matn.domain.audio.VerseSlice
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Byte-range copy between frame offsets — no decode, no re-encode (spec Q1, `split-contract.md`
 * §3–4). Reads only each slice's own range from [source], never the whole file (`research.md` D8).
 */
class FrameAccurateSlicer : AudioSlicer {

    override suspend fun slice(source: ByteSource, index: Mp3FrameIndex, ranges: List<VerseRange>): Resource<List<VerseSlice>> {
        return try {
            val slices = ranges.map { range -> sliceOne(source, index, range) }
            Resource.Success(slices)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(AppError.Storage(t.message ?: "failed to slice the recording"))
        }
    }

    private suspend fun sliceOne(source: ByteSource, index: Mp3FrameIndex, range: VerseRange): VerseSlice {
        var startFrame = index.frameAtMs(range.startMs)
        // Deliberately not `frameAtMs` for the end: that clamps to the *last frame's index*, which
        // is wrong for an exclusive end that legitimately equals `offsets.size` (through the last
        // frame). Rounds the same way, but the upper bound is the frame count, not frame count - 1.
        var endFrameExclusive = (range.endMs / index.frameDurationMs).roundToInt().coerceIn(0, index.offsets.size)
        if (endFrameExclusive <= startFrame) endFrameExclusive = (startFrame + 1).coerceAtMost(index.offsets.size)

        // A Xing/Info/VBRI header only ever sits in the *source's* first frame — drop it from a
        // slice that happens to start there, or the slice reports the source's own duration.
        if (startFrame == 0) {
            val firstOffset = index.offsets[0]
            val header = parseHeader(source.read(firstOffset, 4), 0)
            if (header != null) {
                val frameBytes = source.read(firstOffset, header.frameLengthBytes)
                if (isXingOrVbriFrame(frameBytes, 0, header) && startFrame + 1 < endFrameExclusive) {
                    startFrame += 1
                }
            }
        }

        val byteRange = index.byteRange(startFrame, endFrameExclusive)
        val length = (byteRange.last - byteRange.first + 1).coerceAtLeast(0).toInt()
        val bytes = if (length > 0) source.read(byteRange.first, length) else ByteArray(0)
        val durationMs = ((endFrameExclusive - startFrame) * index.frameDurationMs).roundToLong()

        return VerseSlice(verseId = range.verseId, bytes = bytes, durationMs = durationMs)
    }
}
