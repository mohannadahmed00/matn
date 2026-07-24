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

@Composable
fun GoalsGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(color, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = w * 0.08f))
        drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = w * 0.08f))
        drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.5f))
    }
}

@Composable
fun NotesGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.08f)
        drawRoundRect(color, topLeft = Offset(w * 0.18f, h * 0.14f), size = Size(w * 0.64f, h * 0.72f), cornerRadius = CornerRadius(w * 0.06f), style = stroke)
        val lineX0 = w * 0.30f
        val lineX1 = w * 0.70f
        drawLine(color, Offset(lineX0, h * 0.38f), Offset(lineX1, h * 0.38f), strokeWidth = w * 0.06f)
        drawLine(color, Offset(lineX0, h * 0.54f), Offset(lineX1, h * 0.54f), strokeWidth = w * 0.06f)
        drawLine(color, Offset(lineX0, h * 0.70f), Offset(w * 0.55f, h * 0.70f), strokeWidth = w * 0.06f)
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
