package com.giraffe.matn.domain.model

/**
 * One playable track in a [PlaybackQueue] — exactly one micro-audio file per verse (FR-022 /
 * data-model.md §2.2). Built by `BuildPlaybackQueueUseCase`; `uri` is the resolved playable
 * URI produced by `AudioSourceResolver` (Res.getUri("files/audio/<fileRef>")).
 */
data class AudioTrack(
    val verseId: String,
    val displayNumber: Int,
    val uri: String,
    val durationMs: Long,
)

/**
 * The ordered playlist for one matn / reciter (data-model.md §2.3). Ordering matches
 * `verse.display_number`; [startIndex] is where playback begins (per-verse play taps it at the
 * chosen verse, global play sets 0).
 */
data class PlaybackQueue(
    val matnId: String,
    val tracks: List<AudioTrack>,
    val startIndex: Int,
)