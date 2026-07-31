package com.giraffe.matn.teacher.presentation.split

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.giraffe.matn.domain.audio.VerseRange
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/**
 * Pure function of `(peaks, ranges, durationMs, selectedVerseId)` — a `Canvas`, no state of its
 * own (Principle II). **Left-to-right in both interface languages** (research D11,
 * `design-notes.md` deviation 1): audio timelines read left-to-right in every editor a teacher has
 * used, and mirroring the waveform would also mirror the meaning of dragging a marker "forward".
 */
@Composable
fun WaveformCanvas(
    peaks: FloatArray,
    ranges: List<VerseRange>,
    durationMs: Long,
    selectedVerseId: String?,
    modifier: Modifier = Modifier,
    /** The armed boundary's position — the pointer's while dragging, the committed value once
     * released. Drawn as a full-height marker so the boundary being edited is visible against the
     * waveform for as long as its field holds focus, not only mid-gesture. */
    markerMs: Long? = null,
    /** The transport position on the source's timeline — always drawn, since it is always
     * meaningful. Distinct from [markerMs]: one is where playback *is*, the other is where the
     * boundary being edited sits. */
    playheadMs: Long = 0L,
) {
    val peakColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rangeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val selectedRangeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val boundaryColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.tertiary

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier = modifier.fillMaxWidth().height(MatnSpacing.unit * 12)) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            if (durationMs > 0) {
                ranges.forEach { range ->
                    val startX = (range.startMs.toFloat() / durationMs) * width
                    val endX = (range.endMs.toFloat() / durationMs) * width
                    val color = if (range.verseId == selectedVerseId) selectedRangeColor else rangeColor
                    drawRect(color = color, topLeft = Offset(startX, 0f), size = androidx.compose.ui.geometry.Size(endX - startX, height))
                    drawLine(boundaryColor, Offset(startX, 0f), Offset(startX, height), strokeWidth = Stroke.DefaultMiter)
                    drawLine(boundaryColor, Offset(endX, 0f), Offset(endX, height), strokeWidth = Stroke.DefaultMiter)
                }
            }

            if (peaks.isNotEmpty()) {
                val barWidth = width / peaks.size
                peaks.forEachIndexed { index, peak ->
                    val barHeight = (peak.coerceIn(0f, 1f)) * (height / 2f)
                    val x = index * barWidth + barWidth / 2f
                    drawLine(
                        color = peakColor,
                        start = Offset(x, midY - barHeight),
                        end = Offset(x, midY + barHeight),
                        strokeWidth = barWidth.coerceAtLeast(1f),
                    )
                }
            } else {
                drawLine(peakColor, Offset(0f, midY), Offset(width, midY), strokeWidth = 2f)
            }

            if (durationMs > 0) {
                val x = ((playheadMs.toFloat() / durationMs).coerceIn(0f, 1f)) * width
                drawLine(playheadColor, Offset(x, 0f), Offset(x, height), strokeWidth = 3f)
            }

            // Drawn last so the boundary being edited is never hidden under the playhead.
            if (markerMs != null && durationMs > 0) {
                val x = ((markerMs.toFloat() / durationMs).coerceIn(0f, 1f)) * width
                drawLine(markerColor, Offset(x, 0f), Offset(x, height), strokeWidth = 3f)
            }
        }
    }
}

private fun samplePeaks(count: Int = 100) = FloatArray(count) { i -> (kotlin.math.sin(i * 0.3) * 0.5 + 0.5).toFloat() }

@Preview
@Composable
private fun WaveformWithRangesArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    WaveformCanvas(
        peaks = samplePeaks(),
        ranges = listOf(VerseRange("v1", 0, 3000), VerseRange("v2", 3200, 6000)),
        durationMs = 10_000,
        selectedVerseId = "v1",
    )
}

@Preview
@Composable
private fun WaveformWithRangesEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    WaveformCanvas(
        peaks = samplePeaks(),
        ranges = listOf(VerseRange("v1", 0, 3000), VerseRange("v2", 3200, 6000)),
        durationMs = 10_000,
        selectedVerseId = "v1",
    )
}

@Preview
@Composable
private fun WaveformWithOverlapArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    WaveformCanvas(
        peaks = samplePeaks(),
        ranges = listOf(VerseRange("v1", 0, 4000), VerseRange("v2", 3000, 7000)),
        durationMs = 10_000,
        selectedVerseId = null,
    )
}

@Preview
@Composable
private fun WaveformWithOverlapEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    WaveformCanvas(
        peaks = samplePeaks(),
        ranges = listOf(VerseRange("v1", 0, 4000), VerseRange("v2", 3000, 7000)),
        durationMs = 10_000,
        selectedVerseId = null,
    )
}
