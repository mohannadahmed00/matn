package com.giraffe.matn.domain.model

import kotlin.math.roundToInt

/** Per-matn recall progress (data-model.md §2.1, FR-006/FR-008). */
data class MatnProgress(
    val matnId: String,
    val memorizedCount: Int,
    val totalCount: Int,
) {
    val fraction: Float get() = if (totalCount == 0) 0f else memorizedCount.toFloat() / totalCount
    val percent: Int get() = (fraction * 100).roundToInt()
}
