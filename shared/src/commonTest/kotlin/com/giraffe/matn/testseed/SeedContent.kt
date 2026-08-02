package com.giraffe.matn.testseed

import kotlinx.serialization.Serializable

@Serializable
data class SeedMatn(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverImageRef: String? = null,
    val structureKind: String,
    val defaultReciterId: String,
    val chapters: List<SeedChapter> = emptyList(),
    val verses: List<SeedVerse>,
    /** Phase 8: delivery slug for this matn's on-demand pack (data-model §1.1). */
    val packId: String = "",
    /** Phase 8: measured byte size of the matn's audio, used as the always-offline declared size. */
    val declaredSizeBytes: Long = 0L,
    /** Phase 8: 1 ⇒ bundled install-time starter, non-removable (FR-027/FR-029). */
    val isStarter: Boolean = false,
)

@Serializable
data class SeedChapter(
    val id: String,
    val title: String,
    val order: Int,
)

@Serializable
data class SeedVerse(
    val id: String,
    val chapterId: String? = null,
    val displayNumber: Int,
    val arabicText: String,
    val durationMs: Long,
    val audio: SeedAudio? = null,
)

@Serializable
data class SeedAudio(
    val id: String,
    val fileRef: String,
    val durationMs: Long,
    /** Phase 12: stored object's byte length. Defaulted so existing bundled JSON parses unchanged. */
    val sizeBytes: Long = 0L,
    /** Phase 12: from the first frame header. Defaulted so existing bundled JSON parses unchanged. */
    val sampleRate: Int = 0,
    /** Phase 12: 1 mono, 2 stereo. Defaulted so existing bundled JSON parses unchanged. */
    val channels: Int = 0,
)