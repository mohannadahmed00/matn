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
 * [Source Serif 4](https://github.com/adobe-fonts/source-serif) (SIL OFL), the Stitch design
 * set's `headline-lg`/`body-md` face. **Not currently wired into any [Typography] role**: every
 * concrete `headline-lg`/`body-md` usage fetched from the live Stitch screens
 * (`docs/DESIGN-SOURCE.md`) turned out to be Arabic text, and Source Serif 4 — like Plus Jakarta
 * Sans — has no Arabic glyphs. Wiring it in as those roles' family would silently break Arabic
 * rendering across most of the app (Compose does not fall back across `FontFamily` boundaries the
 * way a browser's CSS font-stack does), so per Constitution Principle VIII ("the constitution
 * outranks the design" for genuine technical necessity, not just style preference) those roles
 * stay on Amiri. This family is bundled and ready for the first genuinely Latin-only UI context
 * that needs it (e.g. a future English locale) rather than left unbundled.
 */
@Composable
fun uiSerifFontFamily(): FontFamily = FontFamily(
    Font(Res.font.SourceSerif4_Variable, FontWeight.Normal),
    Font(Res.font.SourceSerif4_Variable, FontWeight.SemiBold),
)

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
        labelLarge = base.labelLarge.copy(fontFamily = label, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = label, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = label, fontWeight = FontWeight.SemiBold),
    )
}
