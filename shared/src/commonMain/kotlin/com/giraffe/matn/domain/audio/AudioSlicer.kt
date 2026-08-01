package com.giraffe.matn.domain.audio

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.data.audio.Mp3FrameIndex

/** Cuts a continuous recording into per-verse byte payloads at frame boundaries — never the whole
 * source in memory (`research.md` D8). */
interface AudioSlicer {
    suspend fun slice(source: ByteSource, index: Mp3FrameIndex, ranges: List<VerseRange>): Resource<List<VerseSlice>>
}

data class VerseSlice(val verseId: String, val bytes: ByteArray, val durationMs: Long)
