package com.giraffe.matn.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn bottom-nav glyphs, matching [PlaybackGlyphs]'s existing convention (Constitution
 * III/VIII — one shape vocabulary, no system icon font). One per
 * [com.giraffe.matn.presentation.navigation.NavigationTab].
 */
private fun Modifier.desc(text: String?): Modifier =
    if (text != null) this.semantics { contentDescription = text } else this

@Composable
fun LibraryGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f)
        drawRoundRect(color, topLeft = Offset(w * 0.16f, h * 0.16f), size = Size(w * 0.68f, h * 0.68f), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
        drawLine(color, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.84f), strokeWidth = w * 0.07f)
    }
}

/**
 * The Saved tab's four-pointed star (design system §05 — the `✦` in the bottom bar). One mark for
 * bookmarks, notes and memorized verses together: the tab no longer stands for note-taking alone,
 * so the old ruled-page glyph would have named only a third of what it opens.
 */
@Composable
fun SavedGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        // Concave-sided star: each arm runs to the edge and is pulled back toward the centre by a
        // quadratic control point, which is what gives the mark its point rather than a diamond.
        val arm = w * 0.42f
        val waist = w * 0.13f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy - arm)
            quadraticTo(cx + waist, cy - waist, cx + arm, cy)
            quadraticTo(cx + waist, cy + waist, cx, cy + arm)
            quadraticTo(cx - waist, cy + waist, cx - arm, cy)
            quadraticTo(cx - waist, cy - waist, cx, cy - arm)
            close()
        }
        drawPath(path, color)
    }
}

/** A magnifying-glass search affordance — the leading icon of Library's in-place search field. */
@Composable
fun SearchGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.42f, h * 0.42f), style = stroke)
        drawLine(
            color,
            start = Offset(w * 0.62f, h * 0.62f),
            end = Offset(w * 0.84f, h * 0.84f),
            strokeWidth = w * 0.11f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/** A simple chevron back-affordance, shared by any screen with a dedicated top-bar back action.
 *  Points toward the layout's start edge, which follows the interface language rather than being
 *  pinned. */
@Composable
fun BackGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.38f, h * 0.22f)
            lineTo(w * 0.68f, h * 0.5f)
            lineTo(w * 0.38f, h * 0.78f)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
fun SettingsGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        drawCircle(color, radius = w * 0.16f, center = center, style = Stroke(width = w * 0.07f))
        val teeth = 6
        repeat(teeth) { i ->
            val angle = (2 * kotlin.math.PI / teeth) * i
            val inner = w * 0.30f
            val outer = w * 0.42f
            val start = Offset(center.x + inner * kotlin.math.cos(angle).toFloat(), center.y + inner * kotlin.math.sin(angle).toFloat())
            val end = Offset(center.x + outer * kotlin.math.cos(angle).toFloat(), center.y + outer * kotlin.math.sin(angle).toFloat())
            drawLine(color, start, end, strokeWidth = w * 0.09f)
        }
    }
}
