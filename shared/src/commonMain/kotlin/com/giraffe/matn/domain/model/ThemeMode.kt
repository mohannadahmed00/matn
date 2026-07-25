package com.giraffe.matn.domain.model

/**
 * The student's theme-mode preference (Phase 9, FR-006). Persisted by its [name] via
 * [com.giraffe.matn.domain.repository.AppearancePreferencesRepository]. The default value
 * when nothing is stored (or the stored value is unrecognized) is [SYSTEM] — never throws
 * (data-model §2.1).
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT: ThemeMode = SYSTEM

        /** Lenient parse from storage; falls back to the default for unrecognized values. */
        fun fromStorageOrDefault(value: String?): ThemeMode =
            value?.let { v -> entries.firstOrNull { it.name == v } } ?: DEFAULT
    }
}