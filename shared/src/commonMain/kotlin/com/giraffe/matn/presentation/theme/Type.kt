package com.giraffe.matn.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.Amiri_Bold
import matn.shared.generated.resources.Amiri_Regular
import org.jetbrains.compose.resources.Font

/**
 * The bundled [Amiri](https://github.com/aliftype/amiri) (SIL OFL) `FontFamily`, applied
 * **only** to Arabic verse text (Decision 4); interface chrome keeps the platform default.
 *
 * Resource identifiers are produced by Compose's resource codegen, which replaces
 * non-alphanumerics in the filename with `_` — so `Amiri-Regular.ttf` → `Res.font.Amiri_Regular`
 * and `Amiri-Bold.ttf` → `Res.font.Amiri_Bold` (confirmed in the generated `Font0.commonMain.kt`
 * — if codegen ever changes its naming, match the generated names here, per T008).
 *
 * `org.jetbrains.compose.resources.Font` is a `@Composable` accessor, so this is a composable
 * function (called once per screen, not per row — Compose caches the resolved family).
 */
@Composable
fun verseFontFamily(): FontFamily = FontFamily(
    Font(Res.font.Amiri_Regular, FontWeight.Normal),
    Font(Res.font.Amiri_Bold, FontWeight.Bold),
)

/**
 * App-wide Material 3 typography. Body/label styles keep the platform default; the verse
 * `FontFamily` is applied directly in the reading-screen row rather than here, so chrome
 * text stays in the system font (typography here is a placeholder for future phases).
 */
val matnTypography: Typography = Typography()