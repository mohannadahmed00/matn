package com.giraffe.matn.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnShapes

/** The design system's shimmer period (§05 — Library · loading): 1200ms, linear, never eased. */
private const val ShimmerPeriodMs = 1200

/**
 * A placeholder block for content that is **known to be coming** (Matn Design System §05 —
 * "Skeletons only appear when we know content is coming").
 *
 * That condition is the whole point of the component and is the caller's to honour: a skeleton
 * promises a specific shape of content is about to appear, so showing one for a library that is
 * already known to be empty would be a lie the student pays for by waiting. Use it while a refresh
 * of *existing* content is in flight, never as a generic busy indicator.
 *
 * The sweep runs `surfaceContainer → surfaceContainerHigh → surfaceContainer`. Under
 * [LocalReduceMotion] it collapses to a static `surfaceContainer` fill with no sweep at all —
 * a skeleton's job is to hold the layout, and it still does that standing still.
 *
 * Hidden from accessibility: a screen reader announcing four featureless boxes is worse than
 * silence, and the surrounding surface is responsible for saying that a load is in progress.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MatnShapes.lg,
) {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current

    if (reduceMotion) {
        Box(
            modifier = modifier
                .semantics { hideFromAccessibility() }
                .background(color = scheme.surfaceContainer, shape = shape),
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "skeleton")
    // Sweeps the gradient across roughly three block-widths so the highlight enters and leaves
    // rather than pulsing in place. The exact span is arbitrary; what matters is that it exceeds
    // the block so no part of it stays lit for the whole period.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ShimmerPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-sweep",
    )
    val base = scheme.surfaceContainer
    val highlight = scheme.surfaceContainerHigh
    // Built per frame from the animated offset. A `Brush.linearGradient` with explicit start/end
    // works in layout coordinates, so the sweep is direction-agnostic: it does not need mirroring
    // under RTL because it carries no meaning, only motion.
    val sweep = 600f
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x = (progress * 2f - 0.5f) * sweep, y = 0f),
        end = Offset(x = (progress * 2f - 0.5f) * sweep + sweep, y = 0f),
    )
    Box(
        modifier = modifier
            .semantics { hideFromAccessibility() }
            .background(brush = brush, shape = shape),
    )
}
