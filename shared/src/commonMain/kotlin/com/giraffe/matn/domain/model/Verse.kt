package com.giraffe.matn.domain.model

data class Verse(
    val id: String,
    val matnId: String,
    val chapterId: String?,
    val displayNumber: Int,
    val arabicText: String,
    val durationMs: Long,
)