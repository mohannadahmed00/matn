package com.giraffe.matn.domain.model

data class AudioAsset(
    val id: String,
    val verseId: String,
    val reciterId: String,
    val fileRef: String,
    val durationMs: Long,
)