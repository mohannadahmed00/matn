package com.giraffe.matn.domain.search

/**
 * Diacritic-insensitive, letter-form-folded, digit-unified normalization for Arabic (and Latin)
 * text (FR-002/FR-003; contracts/normalization-contract.md § Character rules). Pure, allocation-
 * light, single pass over a [StringBuilder]; no locale/platform-dependent case folding.
 *
 * Matching predicate (owned by callers, e.g. [com.giraffe.matn.data.repository.SearchRepositoryImpl]):
 * `normalize(text).contains(normalize(query))`. Normalized text is **never persisted**
 * (research.md D2) — this is a query-time transform only.
 */
object ArabicNormalizer {

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            when {
                // Diacritics: tanwin, fatha/damma/kasra, shadda, sukun, hamza-below, etc.
                code in 0x064B..0x0655 -> Unit
                // Superscript alef (dagger alef).
                code == 0x0670 -> Unit
                // Tatweel/kashida — a stretching glyph, not a letter.
                code == 0x0640 -> Unit
                // Alef forms -> bare alef: أ إ آ ٱ -> ا
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
                // Alef maksura -> ya: ى -> ي
                ch == 'ى' -> sb.append('ي')
                // Ta marbuta -> ha: ة -> ه
                ch == 'ة' -> sb.append('ه')
                // Arabic-Indic digits ٠-٩ -> ASCII 0-9.
                code in 0x0660..0x0669 -> sb.append('0' + (code - 0x0660))
                // Invariant Latin lowercasing (no Locale dependence).
                ch in 'A'..'Z' -> sb.append(ch + 32)
                // Hamza forms (ء ؤ ئ) and everything else pass through unchanged — out of the
                // clarified FR-002 scope (contract vector 6).
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
