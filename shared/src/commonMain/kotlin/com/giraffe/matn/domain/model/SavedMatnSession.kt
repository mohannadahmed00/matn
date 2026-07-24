package com.giraffe.matn.domain.model

/** One matn's persisted session (data-model.md §2.1). Embeds Phase 3's [RepetitionSettings]
 *  rather than re-declaring counters/range (Principle III). */
data class SavedMatnSession(
    val matnId: String,
    val lastVerseId: String,
    val lastVerseDisplayNumber: Int,
    val positionMs: Long,
    val settings: RepetitionSettings,
)