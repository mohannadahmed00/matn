package com.giraffe.matn.presentation.common

/**
 * Unicode bidirectional **isolate** helpers, for the mixed-direction strings this app is full of:
 * Latin numerals, durations, and byte sizes embedded in Arabic sentences, and Arabic titles
 * embedded in English ones.
 *
 * ## Why this is needed
 *
 * Matn is bilingual: the interface can run Arabic (RTL) or English (LTR), and either language can
 * be showing content authored in the other. When a run of neutral or opposite-direction characters
 * is dropped into a paragraph without isolation, the Unicode Bidirectional Algorithm (UAX #9)
 * resolves it against the *paragraph* direction, not against the run's own. That is what produces
 * the classic failures:
 *
 * - `"765.8 KB"` rendering as `KB 765.8` in an RTL paragraph — the digits and the unit are two
 *   runs, and the neutral space between them is resolved right-to-left.
 * - `"4 verses · 0:31"` rendering as `verses · 0:314` — worse than cosmetic, because the leading
 *   count is torn off and re-attached to the duration, producing a number that is simply false.
 *
 * Wrapping the run in an isolate tells the algorithm to resolve it as a self-contained unit and
 * then place that unit as a single neutral object in the surrounding text. The run's internals can
 * no longer be reordered by, or reorder, anything outside it.
 *
 * ## Which isolate to use
 *
 * - [ltrIsolated] — for runs that are **always** left-to-right regardless of interface language:
 *   durations (`0:31`), byte sizes (`765.8 KB`), percentages (`40%`), version numbers. Digits are
 *   directionally weak, so `"0:31"` alone has no strong character to anchor it and *needs* the
 *   explicit LTR.
 * - [autoIsolated] — for runs whose direction depends on their own content, typically a localized
 *   string or user/teacher-authored text: a matn title, an author name, a pluralized verse count.
 *   The first strong character decides, so the same call works in both languages.
 *
 * ## Composites
 *
 * Isolating the parts is not always enough. In `partA + " · " + partB` the separator is neutral and
 * sits *between* two isolates, so the paragraph direction still decides which part comes first. When
 * the order carries meaning — `required / available`, `count · duration` — isolate the whole
 * expression as well, so the pair keeps its authored order:
 *
 * ```
 * ltrIsolated(formatBytes(required) + " / " + formatBytes(available))
 * ```
 *
 * ## Accessibility
 *
 * The isolate characters are zero-width formatting marks. Screen readers and the `contentDescription`
 * pipeline ignore them, so isolated strings stay safe to reuse as accessibility labels.
 */

/** U+2066 LEFT-TO-RIGHT ISOLATE — opens a run resolved left-to-right. */
private const val LRI = '⁦'

/** U+2067 RIGHT-TO-LEFT ISOLATE — recognized by [isIsolated]; not emitted by these helpers. */
private const val RLI = '⁧'

/** U+2068 FIRST STRONG ISOLATE — opens a run whose direction its first strong character decides. */
private const val FSI = '⁨'

/** U+2069 POP DIRECTIONAL ISOLATE — closes the innermost open isolate. */
private const val PDI = '⁩'

/**
 * Wrap [text] so it always renders left-to-right, whatever the surrounding paragraph direction.
 *
 * Use for numeric runs that have no strong directional character of their own — durations, byte
 * sizes, percentages, counters — where [autoIsolated] would fall through to the paragraph direction
 * and reorder them.
 */
fun ltrIsolated(text: String): String = "$LRI$text$PDI"

/**
 * Wrap [text] so it renders in the direction of its own first strong character, isolated from the
 * surrounding paragraph.
 *
 * Use for localized or authored strings that may be Arabic or Latin depending on the interface
 * language or on who wrote them — matn titles, author names, pluralized counts.
 */
fun autoIsolated(text: String): String = "$FSI$text$PDI"

/**
 * True when [text] as a whole is a single isolated run — that is, an isolate opened at the first
 * character and closed only at the last. Lets call sites avoid double-wrapping.
 *
 * The nesting depth is tracked rather than just checking the two end characters, because a
 * *concatenation* of isolated parts — `ltrIsolated(a) + " / " + ltrIsolated(b)` — also begins with
 * an opener and ends with a closer while being, in bidi terms, three separate runs with a neutral
 * separator between them. Reporting that as already-isolated would let a caller skip the wrapping
 * that keeps `a` and `b` in their authored order, which is the exact failure mode these helpers
 * exist to prevent.
 */
internal fun isIsolated(text: String): Boolean {
    if (text.length < 2) return false
    if (text.first() != LRI && text.first() != RLI && text.first() != FSI) return false
    var depth = 0
    text.forEachIndexed { index, ch ->
        when (ch) {
            LRI, RLI, FSI -> depth++
            PDI -> depth--
        }
        // The run opened at index 0 must stay open until the very end.
        if (depth <= 0 && index != text.lastIndex) return false
    }
    return depth == 0
}
