# Contract: Arabic Normalization (`ArabicNormalizer`)

**Owner**: `domain/search/ArabicNormalizer.kt` (pure Kotlin, `commonMain`)
**Consumers**: `SearchRepositoryImpl` (both query and corpus sides). Never applied to stored data.

## Function

```kotlin
object ArabicNormalizer {
    fun normalize(input: String): String
}
```

Deterministic, allocation-light, no locale/platform dependence (no `String.lowercase(Locale)`
platform variance — use invariant lowercasing for Latin letters only).

## Character rules (FR-002, FR-003 — clarified 2026-07-24)

| Rule | Input | Output |
|------|-------|--------|
| Strip diacritics | U+064B–U+0655 (tanwin, fatha, damma, kasra, shadda, sukun, etc.), U+0670 (superscript alef) | removed |
| Fold alef forms | أ (U+0623), إ (U+0625), آ (U+0622), ٱ (U+0671) | ا (U+0627) |
| Fold ya | ى (U+0649) | ي (U+064A) |
| Fold ta marbuta | ة (U+0629) | ه (U+0647) |
| Digits | ٠–٩ (U+0660–U+0669) | 0–9 |
| Tatweel | ـ (U+0640) | removed |
| Whitespace | runs of whitespace | single space; leading/trailing trimmed |
| Latin case | A–Z | a–z (invariant) |
| Everything else | | passed through unchanged (incl. hamza forms ء/ؤ/ئ — out of scope per spec Assumptions) |

**Matching predicate**: `normalize(text).contains(normalize(query))` — substring containment.

## Required test vectors (`ArabicNormalizerTest`)

| # | Query / input | Text | Must match? | Covers |
|---|---------------|------|-------------|--------|
| 1 | `الاسلام` | `الإِسْلَام` | yes | alef fold + diacritic strip (SC-003 example) |
| 2 | `الإسلام` | `الاسلام` | yes | fold applies to query side too |
| 3 | `فتوه` | `فُتُوَّة` | yes | ta-marbuta fold + shadda/damma strip |
| 4 | `على` | `علي` | yes | ى→ي |
| 5 | `٥` | verse number `5` | yes | digit unification (FR-003) |
| 6 | `مؤمن` | `مءمن` | **no** | hamza forms NOT folded (bounded scope) |
| 7 | `  باب   الطهارة ` | `باب الطهارة` | yes | whitespace collapse/trim |
| 8 | `` (empty) / `"   "` | anything | n/a | normalizes to empty string (caller maps to Idle) |
| 9 | idempotence | `normalize(normalize(x)) == normalize(x)` for all vectors | — | stability |

SC-003 acceptance: for every vector pair, the un-vocalized/folded query returns exactly the
same match set as the exact-form query.
