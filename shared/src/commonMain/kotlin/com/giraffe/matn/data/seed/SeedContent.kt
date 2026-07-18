package com.giraffe.matn.data.seed

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
)