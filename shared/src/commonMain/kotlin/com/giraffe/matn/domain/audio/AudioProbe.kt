package com.giraffe.matn.domain.audio

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteSource

/** The one platform edge every authoring path needs a decoder for: waveform peaks and preview
 * playback (`research.md` D1). Duration/profile/cutting need no decoder — see [ProbeResult]. */
interface AudioProbe {
    /** Duration, profile, and frame count from the frame index alone — no decoding. */
    suspend fun probe(source: ByteSource): Resource<ProbeResult>

    /** Streamed min/max amplitude per bucket, decoding once with bounded memory (`research.md` D7). */
    suspend fun peaks(source: ByteSource, buckets: Int): Resource<FloatArray>
}

data class ProbeResult(
    val durationMs: Long,
    val profile: AudioProfile,
    val frameCount: Int,
)
