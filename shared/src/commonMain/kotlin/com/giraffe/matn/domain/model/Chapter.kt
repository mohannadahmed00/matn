package com.giraffe.matn.domain.model

data class Chapter(
    val id: String,
    val matnId: String,
    val title: String,
    val order: Int,
)