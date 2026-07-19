package com.giraffe.matn.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import matn.shared.generated.resources.Amiri_Bold
import matn.shared.generated.resources.Amiri_Regular
import matn.shared.generated.resources.Res
import org.jetbrains.compose.resources.Font

/**
 * The bundled [Amiri](https://github.com/aliftype/amiri) (SIL OFL) `FontFamily` — a classical
 * Naskh face built for diacriticized Arabic (Decision 4). Matn treats Arabic as the primary
 * voice of the product, so Amiri carries **all substantive Arabic**: verse text, matn titles,
 * author, description, and chapter names. Utility numerals and durations stay in the platform
 * sans (the `label*` roles below), giving a deliberate display/utility pairing rather than
 * rendering everything in one face.
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
 * App-wide Material 3 typography. Arabic-content roles (display / headline / title / body) are
 * set in Amiri with generous line height so heavy تَشْكِيل never crowds the line; `label*` roles
 * keep the platform sans for numerals, durations, and controls.
 */
@Composable
fun matnTypography(): Typography {
    val amiri = verseFontFamily()
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
        // label* intentionally left as the platform sans (numerals / durations / controls).
    )
}
