package com.giraffe.matn.domain.model

data class Matn(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverImageRef: String?,
    val structureKind: StructureKind,
)