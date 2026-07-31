package com.giraffe.matn.domain.audio

/** One verse's requested boundaries within the source recording, in milliseconds — advisory; the
 * actual cut snaps to frame boundaries (`split-contract.md` §3). */
data class VerseRange(val verseId: String, val startMs: Long, val endMs: Long)

/** The picked continuous recording for one splitting session (`data-model.md` §6). **Never**
 * serialized, never persisted — the per-verse lock (FR-018) depends on this. */
data class SourceRecording(
    val localPath: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val profile: AudioProfile,
    val frameCount: Int,
)

/**
 * The transient split state (`data-model.md` §7). Discarded when the screen closes, when the
 * source is replaced (FR-020), and after a successful split — it has no persisted representation
 * anywhere.
 */
data class SplitPlan(
    val source: SourceRecording,
    /** A contiguous run of verse ids in list order, defaulting to the whole matn (FR-013a). */
    val scopeVerseIds: List<String>,
    val ranges: List<VerseRange>,
)
