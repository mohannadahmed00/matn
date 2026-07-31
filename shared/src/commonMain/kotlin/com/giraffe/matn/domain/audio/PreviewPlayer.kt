package com.giraffe.matn.domain.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Gapless sequential playback of stored verse files (`research.md` D9). One implementation serves
 * both the matn-wide preview transport (US3) and a single verse's audition (US1, "Loaded" row
 * state) — a one-verse call is simply `play(listOf(verse), 0)` (Principle III: one player, one
 * seam, not two).
 */
interface PreviewPlayer {
    val state: StateFlow<PreviewState>

    /** **Suspends until playback finishes or is cancelled.** Callers rely on that to know when to
     * clear their own "now playing" state; an implementation that returns immediately leaves every
     * play/stop control permanently out of sync with the audio. */
    suspend fun play(verses: List<PreviewVerse>, startIndex: Int)

    /**
     * Plays one in-memory clip — the split screen's audition of a range it has just sliced
     * (FR-014's "check the cut before committing it"). Takes bytes rather than a `fileRef` because
     * nothing is stored yet: the clip is exactly the payload that *would* be uploaded, so what the
     * teacher hears is byte-identical to what lands in the bucket, bit-reservoir artifact included.
     *
     * Suspends until the clip finishes or is cancelled, for the same reason as [play].
     */
    suspend fun playClip(bytes: ByteArray, displayNumber: Int)

    fun pause()
    fun resume()
    fun stop()
}

data class PreviewVerse(val verseId: String, val displayNumber: Int, val fileRef: String?)

/** `contracts/teacher-ui-contract.md` §3. */
sealed interface PreviewState {
    data object Idle : PreviewState
    data class Buffering(val verseNumber: Int) : PreviewState
    data class Playing(val verseNumber: Int, val positionMs: Long) : PreviewState
    data class Paused(val verseNumber: Int) : PreviewState
    data class MissingAudio(val verseNumber: Int) : PreviewState
}
