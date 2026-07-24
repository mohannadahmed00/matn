package com.giraffe.matn.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme

/**
 * Shared daily-goal completion ring (Phase 7, data-model.md §2.2) — used by BOTH the Home top bar
 * and the Goals dashboard (Constitution VIII, extract at second use). Stateless and parameterized:
 * driven entirely by [fraction]/[practiced]/[goal]/[isComplete]; no ViewModel, no repository.
 * [diameter] defaults to the Goals-dashboard scale; Home passes a smaller token multiple.
 */
@Composable
fun DailyGoalRing(
    fraction: Float,
    practiced: Int,
    goal: Int,
    isComplete: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = MatnSpacing.unit * 12,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.12f)
            drawArc(
                color = scheme.primary.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            if (fraction > 0f) {
                drawArc(
                    color = if (isComplete) scheme.tertiary else scheme.primary,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        Text(
            text = "$practiced/$goal",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun DailyGoalRingEmptyPreview() {
    MatnTheme { DailyGoalRing(fraction = 0f, practiced = 0, goal = 10, isComplete = false) }
}

@Preview
@Composable
private fun DailyGoalRingPartialPreview() {
    MatnTheme { DailyGoalRing(fraction = 0.4f, practiced = 4, goal = 10, isComplete = false) }
}

@Preview
@Composable
private fun DailyGoalRingCompletePreview() {
    MatnTheme { DailyGoalRing(fraction = 1f, practiced = 10, goal = 10, isComplete = true) }
}
