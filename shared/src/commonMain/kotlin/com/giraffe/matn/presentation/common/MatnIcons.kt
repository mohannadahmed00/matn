package com.giraffe.matn.presentation.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Material Symbols this app draws, as multiplatform [ImageVector]s.
 *
 * ## Why these are vendored rather than depended on
 *
 * `docs/DESIGN-SOURCE.md` names **Material Symbols Outlined** as the canonical icon family, and the
 * intent is to honour that rather than keep hand-rolling `Canvas` glyphs (which is how the Settings
 * tab ended up with a *sun* for its icon). The obvious route — depend on the Material icon library
 * — is closed:
 *
 * - `org.jetbrains.compose.material:material-icons-core` and `-extended`, the multiplatform
 *   artifacts, were **discontinued at 1.7.3**. This project is on Compose Multiplatform 1.11.1.
 * - `androidx.compose.material:material-icons-core` is still maintained but is **Android-only**, so
 *   it cannot be referenced from `commonMain`, where every screen in this module lives.
 * - Even setting versioning aside, `-core` carries only the ~140 most common glyphs; `Palette`,
 *   `Storage`, `FormatSize`, and `Speed` — four of the icons Settings needs — are `-extended`.
 *
 * So the geometry itself is vendored: the path data below is the official Material Symbols outline
 * for each glyph, on the standard 24×24 viewport. That gives real Material geometry, consistent
 * optical sizing, and correct RTL mirroring, with no dead dependency and no per-target source sets.
 * The cost is that adding a glyph means adding its path here — deliberate, since an icon set that
 * grows only when someone decides it should is the point.
 *
 * ## Mirroring
 *
 * Directional glyphs set `autoMirror`, so they flip with the layout direction. Matn runs LTR or RTL
 * depending on the interface language ([com.giraffe.matn.presentation.theme.MatnTheme]), and a back
 * chevron that points the wrong way in Arabic is worse than no chevron. Non-directional glyphs
 * leave it off — a bell or a trash can must not mirror.
 */
object MatnIcons {

    /** Text-size control — the reading preferences group. */
    val FormatSize: ImageVector by lazy {
        materialIcon("FormatSize", "M9 4v3h5v12h3V7h5V4H9zm-6 8h3v7h3v-7h3V9H3v3z")
    }

    /** Colour palette — the appearance/theme group. */
    val Palette: ImageVector by lazy {
        materialIcon(
            "Palette",
            "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01" +
                "-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8z" +
                "m-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12z" +
                "m3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8z" +
                "m5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8z" +
                "m3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
        )
    }

    /** Speedometer — the playback group (default speed and repetition). */
    val Speed: ImageVector by lazy {
        materialIcon(
            "Speed",
            "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23" +
                "A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.43z" +
                "m-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z",
        )
    }

    /** Stacked drives — the storage group. */
    val Storage: ImageVector by lazy {
        materialIcon(
            "Storage",
            "M2 20h20v-4H2v4zm2-3h2v2H4v-2zM2 4v4h20V4H2zm4 3H4V5h2v2zM2 14h20v-4H2v4zm2-3h2v2H4v-2z",
        )
    }

    /** Bell — the notifications row. Never mirrored. */
    val Notifications: ImageVector by lazy {
        materialIcon(
            "Notifications",
            "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4" +
                "c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
        )
    }

    /** Circled "i" — the about group. */
    val Info: ImageVector by lazy {
        materialIcon(
            "Info",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" +
                "m1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
        )
    }

    /** Trash can — destructive removal. Never mirrored. */
    val Delete: ImageVector by lazy {
        materialIcon(
            "Delete",
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
        )
    }

    /** Forward chevron — a row that opens something. Mirrors with the layout direction. */
    val ChevronForward: ImageVector by lazy {
        materialIcon("ChevronForward", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z", autoMirror = true)
    }

    /** Back arrow — top-bar navigation. Mirrors with the layout direction. */
    val ArrowBack: ImageVector by lazy {
        materialIcon(
            "ArrowBack",
            "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
            autoMirror = true,
        )
    }

    /** Tick — a completed/confirmed state. */
    val Check: ImageVector by lazy {
        materialIcon("Check", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z")
    }

    /** Circular arrow — replay the introduction. */
    val Replay: ImageVector by lazy {
        materialIcon(
            "Replay",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z",
        )
    }
}

/**
 * Build a 24×24 [ImageVector] from Material Symbols [pathData].
 *
 * The vector is filled with opaque black; every call site renders it through `Icon(...)`, which
 * replaces the fill with its `tint`, so the colour here is only a placeholder. Set [autoMirror] for
 * directional glyphs — see the mirroring note on [MatnIcons].
 */
private fun materialIcon(name: String, pathData: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
