package com.giraffe.matn.domain.model

/**
 * Shared verse-context block for search results and global bookmark/note list rows
 * (data-model.md § Domain models). Pure Kotlin, no dependency on storage/UI types.
 */
data class AnnotatedVerseRef(
    val matnId: String,
    val matnTitle: String,
    val verseId: String,
    val verseNumber: Int,
    val verseText: String,
)
