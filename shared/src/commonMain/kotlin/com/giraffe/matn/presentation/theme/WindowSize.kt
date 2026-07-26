package com.giraffe.matn.presentation.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Available-width window class (research D8, contract adaptive-motion §A1). Derived from
 * `BoxWithConstraints`'s `maxWidth` at the app root, *not* from a device category — a narrow
 * split-screen pane on a tablet gets the COMPACT layout (FR-026). Recomputed on every resize, so
 * rotation and multi-window transitions are ordinary recompositions rather than special cases.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * Maps an available width to its [WindowWidthClass]. Boundaries match the Material 3 window-size
 * breakpoints (600dp / 840dp) and are unit-tested at every boundary by
 * [com.giraffe.matn.theme.WindowWidthClassTest].
 */
fun widthClassFor(width: Dp): WindowWidthClass = when {
    width < 600.dp -> WindowWidthClass.COMPACT
    width < 840.dp -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

/**
 * Provided at the app root from `BoxWithConstraints`'s `maxWidth`; defaults to [WindowWidthClass.COMPACT]
 * so the 88 existing `@Preview`s keep working untouched (research D12).
 *
 * Plain [compositionLocalOf], not `staticCompositionLocalOf`: this value genuinely changes during
 * the composition's lifetime (every resize/rotation/multi-window transition per the doc comment
 * above), so it needs Compose's scoped-invalidation tracking — `staticCompositionLocalOf` would
 * force a full recomposition of everything under the provider on every resize instead of just the
 * composables that actually read this value.
 */
val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.COMPACT }