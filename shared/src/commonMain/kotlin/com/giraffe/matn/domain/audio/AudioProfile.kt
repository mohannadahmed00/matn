package com.giraffe.matn.domain.audio

import com.giraffe.matn.domain.catalog.MatnDraft

/** A matn's encoding profile — every audio-bearing verse in it must share one (FR-005a). */
data class AudioProfile(val sampleRate: Int, val channels: Int)

/** The profile of the first audio-bearing verse in list order, or `null` when the matn holds no
 * audio (`data-model.md` §5) — derived, never declared, so it cannot drift from the files. */
fun MatnDraft.audioProfile(): AudioProfile? =
    verses.firstNotNullOfOrNull { verse ->
        verse.audio?.let { AudioProfile(sampleRate = it.sampleRate, channels = it.channels) }
    }
