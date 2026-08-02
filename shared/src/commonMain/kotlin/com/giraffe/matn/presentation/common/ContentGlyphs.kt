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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.giraffe.matn.presentation.theme.MatnTheme

/**
 * Phase 8 (Storage & Downloads) content-delivery glyphs — hand-drawn, matching [PlaybackGlyphs] /
 * [NavGlyphs] / [AnnotationGlyphs]'s convention (Constitution III/VIII: one shape vocabulary, no
 * system icon font). Reused across [ContentAvailabilityBadge], [ContentActionButton], and the
 * Settings breakdown rather than each surface inventing its own icon (Principle VIII).
 */
private fun Modifier.desc(text: String?): Modifier =
    if (text != null) this.semantics { contentDescription = text } else this

/** A downward arrow into a tray — "not installed, tap to install" (mirrors the design-notes.md
 *  fetched `download` status icon). */
@Composable
fun DownloadGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.60f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(w * 0.28f, h * 0.40f)
                lineTo(w * 0.5f, h * 0.64f)
                lineTo(w * 0.72f, h * 0.40f)
            },
            color,
            style = stroke,
        )
        drawLine(color, Offset(w * 0.20f, h * 0.84f), Offset(w * 0.80f, h * 0.84f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    }
}

/** A checkmark inside a circle — "installed and present" (mirrors the fetched `cloud_done` status
 *  icon; deliberately distinct from [DownloadGlyph], never a recolor of it). */
@Composable
fun DownloadedGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        drawCircle(color, radius = w * 0.42f, center = center, style = Stroke(width = w * 0.08f))
        drawPath(
            Path().apply {
                moveTo(w * 0.32f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.70f, h * 0.38f)
            },
            color,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round),
        )
    }
}

/** An X inside a circle — cancel an in-flight install. */
@Composable
fun CancelInstallGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(color, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = w * 0.08f))
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.36f), Offset(w * 0.64f, h * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.64f, h * 0.36f), Offset(w * 0.36f, h * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

/** A trash-can shape — remove an installed matn's content and reclaim its space. */
@Composable
fun RemoveContentGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.24f, h * 0.30f), Offset(w * 0.76f, h * 0.30f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.40f, h * 0.30f), Offset(w * 0.44f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.60f, h * 0.30f), Offset(w * 0.56f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.30f, h * 0.32f),
            size = Size(w * 0.40f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = stroke,
        )
        drawLine(color, Offset(w * 0.42f, h * 0.44f), Offset(w * 0.42f, h * 0.76f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.58f, h * 0.44f), Offset(w * 0.58f, h * 0.76f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
    }
}

/**
 * An open circular arrow — "fetch the catalog again". Phase 13 exposed this only inside the two
 * empty states and the sync-failure notice, which left a student whose library already has متون
 * with no way to ask for a newly published one before the staleness window expires.
 */
@Composable
fun RefreshGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Canvas(modifier.size(size).desc(contentDescription)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        // Open at the top-right so the arrowhead has somewhere to sit.
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.68f, h * 0.68f),
            style = stroke,
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.60f, h * 0.10f)
                lineTo(w * 0.86f, h * 0.26f)
                lineTo(w * 0.60f, h * 0.40f)
            },
            color,
            style = stroke,
        )
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun ContentGlyphsPreview() {
    MatnTheme {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                com.giraffe.matn.presentation.theme.MatnSpacing.unit,
            ),
        ) {
            val scheme = androidx.compose.material3.MaterialTheme.colorScheme
            DownloadGlyph(color = scheme.onSurfaceVariant)
            DownloadedGlyph(color = scheme.primary)
            CancelInstallGlyph(color = scheme.onSurfaceVariant)
            RemoveContentGlyph(color = scheme.error)
        }
    }
}
