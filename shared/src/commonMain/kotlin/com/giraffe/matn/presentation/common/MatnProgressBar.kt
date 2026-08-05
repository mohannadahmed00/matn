package com.giraffe.matn.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import kotlin.math.roundToInt

/**
 * Shared determinate progress bar (Phase 7, data-model.md §2.1) — used by BOTH the matn details
 * header and the Goals dashboard per-matn rows (Constitution VIII, extract at second use).
 * Stateless and parameterized: driven entirely by [fraction] and the optional [label].
 */
@Composable
fun MatnProgressBar(fraction: Float, modifier: Modifier = Modifier, label: String? = null) {
    // T095 (US5, adaptive-motion-contract.md §B2.1/§B3): animates toward the true value at
    // durationShort, never past it (FR-034); reduce motion snaps immediately, the information
    // (the percentage text, the fill) is never suppressed, only the interpolation.
    val reduceMotion = LocalReduceMotion.current
    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (reduceMotion) 0 else MatnMotion.durationShort),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        // T065 (US2, FR-016): the percentage is shown whether or not a [label] is supplied, so
        // the fill never communicates progress by colour/length alone.
        Row(modifier = Modifier.fillMaxWidth()) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = ltrIsolated("${(target * 100).roundToInt()}%"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MatnSpacing.unit / 2)
                .clip(MatnShapes.full),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun MatnProgressBarZeroPreview() {
    MatnTheme { MatnProgressBar(fraction = 0f, label = "الأجرومية") }
}

@Preview
@Composable
private fun MatnProgressBarPartialPreview() {
    MatnTheme { MatnProgressBar(fraction = 0.45f, label = "الأجرومية") }
}

@Preview
@Composable
private fun MatnProgressBarFullPreview() {
    MatnTheme { MatnProgressBar(fraction = 1f, label = "الأجرومية") }
}
