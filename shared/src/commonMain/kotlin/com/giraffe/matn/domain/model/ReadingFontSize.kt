package com.giraffe.matn.domain.model

/**
 * The four adjustable Arabic reading font-size steps (FR-016/FR-017, SC-007).
 * Persisted by its [name] via [com.giraffe.matn.domain.repository.ReadingPreferencesRepository].
 * The default value when nothing is stored (or the stored value is unrecognized) is [MEDIUM].
 */
enum class ReadingFontSize {
    SMALL,
    MEDIUM,
    LARGE,
    XLARGE;

    companion object {
        val DEFAULT: ReadingFontSize = MEDIUM

        /** Lenient parse from storage; falls back to the default for unrecognized values. */
        fun fromStorageOrDefault(value: String?): ReadingFontSize =
            value?.let { v -> entries.firstOrNull { it.name == v } } ?: DEFAULT
    }
}