package com.giraffe.matn.data.repository

import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.repository.RepetitionSettingsStore

/** This phase's in-memory [RepetitionSettingsStore] implementation (FR-033). */
class InMemoryRepetitionSettingsStore : RepetitionSettingsStore {
    private val map = mutableMapOf<String, RepetitionSettings>()

    override fun get(matnId: String): RepetitionSettings = map[matnId] ?: RepetitionSettings()

    override fun put(matnId: String, settings: RepetitionSettings) {
        map[matnId] = settings
    }
}
