package com.giraffe.matn.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn transport glyphs as vector paths — deliberately **not** system emoji, so playback
 * controls carry the manuscript palette exactly (ink / scholar-green / gold) and never fall back to
 * the OS's multicolor emoji font (which is what made the old ⏸/⏹/▶ read as stray orange blobs).
 *
 * Each is a pure function of [color] + [size]; shared by the player bar and the per-verse play
 * affordance (Constitution III — one source for the shape vocabulary). [contentDescription] adds a
 * TalkBack label when the glyph is the clickable content of a control.
 */
private fun Modifier.desc(text: String?): Modifier =
    if (text != null) this.semantics { contentDescription = text } else this

@Composable
fun PlayGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        // A poised isosceles triangle, insets kept off the edges so it reads as drawn, not clipped.
        drawPath(
            Path().apply {
                moveTo(w * 0.28f, h * 0.16f)
                lineTo(w * 0.28f, h * 0.84f)
                lineTo(w * 0.82f, h * 0.50f)
                close()
            },
            color,
        )
    }
}

@Composable
fun PauseGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.15f
        val top = h * 0.18f
        val barH = h * 0.64f
        val r = CornerRadius(barW * 0.4f)
        drawRoundRect(color, topLeft = Offset(w * 0.32f - barW / 2, top), size = Size(barW, barH), cornerRadius = r)
        drawRoundRect(color, topLeft = Offset(w * 0.68f - barW / 2, top), size = Size(barW, barH), cornerRadius = r)
    }
}

@Composable
fun StopGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val s = w * 0.56f
        drawRoundRect(color, topLeft = Offset((w - s) / 2, (h - s) / 2), size = Size(s, s), cornerRadius = CornerRadius(s * 0.18f))
    }
}

@Composable
fun SkipNextGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        drawPath(
            Path().apply {
                moveTo(w * 0.22f, h * 0.20f)
                lineTo(w * 0.22f, h * 0.80f)
                lineTo(w * 0.60f, h * 0.50f)
                close()
            },
            color,
        )
        drawRoundRect(color, topLeft = Offset(w * 0.66f, h * 0.20f), size = Size(w * 0.12f, h * 0.60f), cornerRadius = CornerRadius(w * 0.05f))
    }
}

/** Two counter-rotating arcs with arrowheads — the repetition-setup entry point (specs/010 US2). */
@Composable
fun RepeatGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        val topRect = Rect(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.62f)
        drawArc(color, startAngle = 200f, sweepAngle = 160f, useCenter = false, style = stroke, topLeft = topRect.topLeft, size = topRect.size)
        val bottomRect = Rect(w * 0.15f, h * 0.38f, w * 0.85f, h * 0.85f)
        drawArc(color, startAngle = 20f, sweepAngle = 160f, useCenter = false, style = stroke, topLeft = bottomRect.topLeft, size = bottomRect.size)
        drawPath(
            Path().apply {
                moveTo(w * 0.85f, h * 0.15f)
                lineTo(w * 0.97f, h * 0.30f)
                lineTo(w * 0.72f, h * 0.32f)
                close()
            },
            color,
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.15f, h * 0.85f)
                lineTo(w * 0.03f, h * 0.70f)
                lineTo(w * 0.28f, h * 0.68f)
                close()
            },
            color,
        )
    }
}

@Composable
fun SkipPreviousGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(color, topLeft = Offset(w * 0.22f, h * 0.20f), size = Size(w * 0.12f, h * 0.60f), cornerRadius = CornerRadius(w * 0.05f))
        drawPath(
            Path().apply {
                moveTo(w * 0.78f, h * 0.20f)
                lineTo(w * 0.78f, h * 0.80f)
                lineTo(w * 0.40f, h * 0.50f)
                close()
            },
            color,
        )
    }
}
