package com.giraffe.matn.domain.catalog

/** Derived from `verses`, never set by hand, so it cannot drift from reality (`data-model.md` §4). */
enum class AudioCompleteness {
    NONE,
    PARTIAL,
    COMPLETE;

    companion object {
        fun of(verses: List<DraftVerse>): AudioCompleteness {
            if (verses.isEmpty()) return NONE
            val withAudio = verses.count { it.audio != null }
            return when {
                withAudio == 0 -> NONE
                withAudio == verses.size -> COMPLETE
                else -> PARTIAL
            }
        }
    }
}
