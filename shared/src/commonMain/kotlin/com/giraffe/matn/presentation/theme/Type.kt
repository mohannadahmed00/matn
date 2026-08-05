package com.giraffe.matn.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import matn.shared.generated.resources.Amiri_Bold
import matn.shared.generated.resources.Amiri_Regular
import matn.shared.generated.resources.PlusJakartaSans_Variable
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.SourceSerif4_Variable
import org.jetbrains.compose.resources.Font

/**
 * The bundled [Amiri](https://github.com/aliftype/amiri) (SIL OFL) `FontFamily` — a classical
 * Naskh face built for diacriticized Arabic. Matn treats Arabic as the primary voice of the
 * product, so Amiri carries **all substantive Arabic** anywhere in the app: verse text, matn
 * titles, author, description, chapter names, and any short Arabic label (see
 * [arabicLabelSmall] below) — it is the only bundled face with real Arabic glyph coverage.
 *
 * `org.jetbrains.compose.resources.Font` is a `@Composable` accessor, so this is a composable
 * function; Compose caches the resolved family, so repeated calls are cheap.
 */
@Composable
fun verseFontFamily(): FontFamily = FontFamily(
    Font(Res.font.Amiri_Regular, FontWeight.Normal),
    Font(Res.font.Amiri_Bold, FontWeight.Bold),
)

/**
 * [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans) (SIL OFL), the canonical
 * `label-sm` face from the Stitch design set (`docs/DESIGN-SOURCE.md`) — used for **Latin-only**
 * UI chrome: buttons, chips, nav labels, numerals, durations. It has no Arabic glyphs, so any
 * Arabic text at label scale MUST use [arabicLabelSmall] instead, never this family directly.
 *
 * Bundled as a single variable-weight file; Compose/Skia resolves the Normal/SemiBold instances
 * from its `wght` axis the same way it resolves any other `Font` weight request.
 */
@Composable
fun labelFontFamily(): FontFamily = FontFamily(
    Font(Res.font.PlusJakartaSans_Variable, FontWeight.Normal),
    Font(Res.font.PlusJakartaSans_Variable, FontWeight.SemiBold),
)

/**
 * [Source Serif 4](https://github.com/adobe-fonts/source-serif) (SIL OFL) — the design system's
 * headline and body face for **Latin text only**. It has no Arabic glyphs.
 *
 * **Deliberately not wired into any [Typography] role.** The M3 roles are shared by both scripts,
 * and Compose does not fall back across `FontFamily` boundaries the way a browser's CSS font-stack
 * does — so assigning Source Serif 4 to `bodyLarge` would silently break every Arabic surface that
 * reads the ambient typography. The design system specifies it for those roles because it is
 * reasoning about a stylesheet, where fallback is free; in Compose the same assignment is a
 * rendering bug.
 *
 * Reach for it through [MatnLatinType] at sites where the content is *known* to be Latin. The
 * failure mode of that arrangement is benign — a Latin string that misses the opt-in renders in
 * Amiri, which is merely less pretty — whereas the inverse arrangement fails by rendering Arabic
 * as tofu.
 */
@Composable
fun uiSerifFontFamily(): FontFamily = FontFamily(
    Font(Res.font.SourceSerif4_Variable, FontWeight.Normal),
    Font(Res.font.SourceSerif4_Variable, FontWeight.SemiBold),
)

/**
 * The Latin display/heading/body styles (Matn Design System §03 — Latin scale), for content known
 * at the call site to be Latin: English UI copy, transliterated titles, Studio's LTR-pinned chrome.
 *
 * Arabic content must **not** use these — see [uiSerifFontFamily] for why. Anything that might hold
 * either script keeps the ambient `MaterialTheme.typography`, which is Amiri and renders both.
 */
object MatnLatinType {

    /** 32/38 · w600 · -0.32sp. Screen titles. */
    @Composable
    fun displaySmall(): TextStyle = TextStyle(
        fontFamily = uiSerifFontFamily(),
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.32).sp,
    )

    /** 24/30 · w600. Section headings. */
    @Composable
    fun headlineSmall(): TextStyle = TextStyle(
        fontFamily = uiSerifFontFamily(),
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 20/26 · w600. Sheet and dialog titles. */
    @Composable
    fun titleLarge(): TextStyle = TextStyle(
        fontFamily = uiSerifFontFamily(),
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 16/26 · w400. Body copy — descriptions, explanatory text. */
    @Composable
    fun bodyLarge(): TextStyle = TextStyle(
        fontFamily = uiSerifFontFamily(),
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 14/22 · w400. Secondary body — metadata lines, captions. */
    @Composable
    fun bodyMedium(): TextStyle = TextStyle(
        fontFamily = uiSerifFontFamily(),
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    )
}

/**
 * Explicit small-Arabic-label style (verse-number badges, in-carousel meta chips like "البيت ٢")
 * for places that need label-scale sizing but Arabic glyphs — `MaterialTheme.typography.label*`
 * below is Plus Jakarta Sans and cannot render them. Matches the Stitch `label-sm` metrics
 * (12sp/16sp line height/600 weight) with Amiri swapped in for the family.
 */
@Composable
fun arabicLabelSmall(): TextStyle = TextStyle(
    fontFamily = verseFontFamily(),
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Bold,
)

/**
 * App-wide Material 3 typography. Arabic-content roles (display / headline / title / body) stay
 * on Amiri with generous line height so heavy تَشْكِيل never crowds the line; `label*` roles are
 * the bundled Plus Jakarta Sans (Latin UI chrome — buttons, chips, nav labels, numerals).
 */
@Composable
fun matnTypography(): Typography {
    val amiri = verseFontFamily()
    val label = labelFontFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = amiri),
        displayMedium = base.displayMedium.copy(fontFamily = amiri),
        displaySmall = base.displaySmall.copy(fontFamily = amiri),
        headlineLarge = base.headlineLarge.copy(fontFamily = amiri),
        headlineMedium = base.headlineMedium.copy(fontFamily = amiri),
        headlineSmall = base.headlineSmall.copy(fontFamily = amiri, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = amiri),
        titleMedium = base.titleMedium.copy(fontFamily = amiri),
        titleSmall = base.titleSmall.copy(fontFamily = amiri),
        bodyLarge = base.bodyLarge.copy(fontFamily = amiri, lineHeight = 32.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = amiri, lineHeight = 26.sp),
        // Label metrics per design system §03. The tracking widens as the size drops — at 11sp,
        // uppercase state text needs the extra letter-spacing to stay readable as a word rather
        // than a smear.
        labelLarge = base.labelLarge.copy(
            fontFamily = label,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.14.sp,
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.96.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        ),
    )
}
