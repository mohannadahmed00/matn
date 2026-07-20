package com.giraffe.matn.domain.repository

import com.giraffe.matn.domain.model.RepetitionSettings

/**
 * Per-matn drill configuration, keyed by `matnId` (FR-007 / data-model.md §5). This phase keeps
 * it in-memory only (FR-033); the interface is the Phase 4 persistence seam — a SQLDelight-backed
 * implementation substitutes in with no change to any caller (research D4).
 */
interface RepetitionSettingsStore {
    /** Returns the defaults for an unknown matn — never null, never throws. */
    fun get(matnId: String): RepetitionSettings
    fun put(matnId: String, settings: RepetitionSettings)
}
