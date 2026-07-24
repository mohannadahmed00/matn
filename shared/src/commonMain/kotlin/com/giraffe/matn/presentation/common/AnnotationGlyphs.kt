package com.giraffe.matn.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bookmark/note indicator + action glyphs for the reading carousel (US2/US3, FR-011/FR-016) —
 * hand-drawn, matching [PlaybackGlyphs]'s convention (Constitution III/VIII: one shape
 * vocabulary, no system icon font). The two glyphs are deliberately different shapes (ribbon vs.
 * pencil-on-page), never just a color/fill variant of the same outline, so they read as visually
 * distinct even to a colorblind reader (FR-016 "visually distinct").
 */
private fun Modifier.desc(text: String?): Modifier =
    if (text != null) this.semantics { contentDescription = text } else this

/** A ribbon/flag bookmark shape. [filled] = actively bookmarked; unfilled = outline-only action. */
@Composable
fun BookmarkGlyph(color: Color, filled: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.12f)
            lineTo(w * 0.72f, h * 0.12f)
            lineTo(w * 0.72f, h * 0.88f)
            lineTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.28f, h * 0.88f)
            close()
        }
        if (filled) {
            drawPath(path, color)
        } else {
            drawPath(path, color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
        }
    }
}

/** A pencil-on-page shape for the "has note" indicator / note-editor entry point. */
@Composable
fun NoteGlyph(color: Color, filled: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val pageStroke = Stroke(width = w * 0.07f)
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.18f, h * 0.14f),
            size = Size(w * 0.64f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else pageStroke,
        )
        if (filled) {
            // A pencil stroke crossing the filled page so the two states aren't a pure color swap.
            drawLine(
                Color.White.copy(alpha = 0.9f),
                start = Offset(w * 0.32f, h * 0.62f),
                end = Offset(w * 0.68f, h * 0.30f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round,
            )
        } else {
            val lineX0 = w * 0.30f
            val lineX1 = w * 0.70f
            drawLine(color, Offset(lineX0, h * 0.38f), Offset(lineX1, h * 0.38f), strokeWidth = w * 0.06f)
            drawLine(color, Offset(lineX0, h * 0.54f), Offset(lineX1, h * 0.54f), strokeWidth = w * 0.06f)
            drawLine(color, Offset(lineX0, h * 0.70f), Offset(w * 0.55f, h * 0.70f), strokeWidth = w * 0.06f)
        }
    }
}

/**
 * A check-in-circle shape for the "memorized" indicator/action (Phase 7, FR-002). Deliberately a
 * distinct shape from [BookmarkGlyph] (ribbon) and [NoteGlyph] (page) — not a recolor of either —
 * so it reads as visually distinct even to a colorblind reader. [filled] = memorized (solid);
 * unfilled = outline-only toggle action.
 */
@Composable
fun MemorizedGlyph(color: Color, filled: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        val radius = w * 0.42f
        val check = Path().apply {
            moveTo(w * 0.30f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.66f)
            lineTo(w * 0.72f, h * 0.36f)
        }
        if (filled) {
            drawCircle(color, radius = radius, center = center)
            drawPath(check, Color.White, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
        } else {
            drawCircle(color, radius = radius, center = center, style = Stroke(width = w * 0.07f))
            drawPath(check, color, style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))
        }
    }
}
